(ns core.entity
  (:require [malli.core :as m]
            [core.math.geom :as geom]
            [core.math.vector :as v]
            [core.utils.core :as utils]
            [core.ctx :refer :all]
            [core.graphics.camera :as camera]
            [core.graphics.views :refer [world-camera world-viewport-width world-viewport-height]]
            [core.ctx.raycaster :refer [ray-blocked?]]))

(defsystem create "Create entity with eid for txs side-effects. Default nil."
  [_ entity ctx])
(defmethod create :default [_ entity ctx])

(defsystem destroy "FIXME" [_ entity ctx])
(defmethod destroy :default [_ entity ctx])

(defsystem tick "FIXME" [_ entity ctx])
(defmethod tick :default [_ entity ctx])

; java.lang.IllegalArgumentException: No method in multimethod 'render-info' for dispatch value: :position
; actually we dont want this to be called over that
; it should be :components? then ?
; => shouldn't need default fns for render -> don't call it if its not there

; every component has parent-entity-id (peid)
; fetch active entity-ids
; then fetch all components which implement render-below
; and have parent-id in entity-ids, etc.

(defsystem render-below "FIXME" [_ entity* g ctx])
(defmethod render-below :default [_ entity* g ctx])

(defsystem render "FIXME" [_ entity* g ctx])
(defmethod render :default [_ entity* g ctx])

(defsystem render-above "FIXME" [_ entity* g ctx])
(defmethod render-above :default [_ entity* g ctx])

(defsystem render-info "FIXME" [_ entity* g ctx])
(defmethod render-info :default [_ entity* g ctx])

(def render-systems [render-below
                     render
                     render-above
                     render-info])

; so that at low fps the game doesn't jump faster between frames used @ movement to set a max speed so entities don't jump over other entities when checking collisions
(def max-delta-time 0.04)

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
; TODO assert at properties load
(def ^:private min-solid-body-size 0.39) ; == spider smallest creature size.

; set max speed so small entities are not skipped by projectiles
; could set faster than max-speed if I just do multiple smaller movement steps in one frame
(def ^:private max-speed (/ min-solid-body-size max-delta-time)) ; need to make var because m/schema would fail later if divide / is inside the schema-form
(def movement-speed-schema (m/schema [:and number? [:>= 0] [:<= max-speed]]))

(def hpbar-height-px 5)

(def z-orders [:z-order/on-ground
               :z-order/ground
               :z-order/flying
               :z-order/effect])

(def render-order (utils/define-order z-orders))

(defrecord Entity [position
                   left-bottom
                   width
                   height
                   half-width
                   half-height
                   radius
                   collides?
                   z-order
                   rotation-angle])

(defn ->Body [{[x y] :position
               :keys [position
                      width
                      height
                      collides?
                      z-order
                      rotation-angle]}]
  (assert position)
  (assert width)
  (assert height)
  (assert (>= width  (if collides? min-solid-body-size 0)))
  (assert (>= height (if collides? min-solid-body-size 0)))
  (assert (or (boolean? collides?) (nil? collides?)))
  (assert ((set z-orders) z-order))
  (assert (or (nil? rotation-angle)
              (<= 0 rotation-angle 360)))
  (map->Entity
   {:position (mapv float position)
    :left-bottom [(float (- x (/ width  2)))
                  (float (- y (/ height 2)))]
    :width  (float width)
    :height (float height)
    :half-width  (float (/ width  2))
    :half-height (float (/ height 2))
    :radius (float (max (/ width  2)
                        (/ height 2)))
    :collides? collides?
    :z-order z-order
    :rotation-angle (or rotation-angle 0)}))

(def ^{:doc "For effects just to have a mouseover body size for debugging purposes."}
  effect-body-props
  {:width 0.5
   :height 0.5
   :z-order :z-order/effect})

(defn tile [entity*]
  (utils/->tile (:position entity*)))

(defn direction [entity* other-entity*]
  (v/direction (:position entity*) (:position other-entity*)))

(defn collides? [entity* other-entity*]
  (geom/collides? entity* other-entity*))

(defprotocol State
  (state [_])
  (state-obj [_]))

(defprotocol Skills
  (has-skill? [_ skill]))

(defprotocol Faction
  (enemy-faction [_])
  (friendly-faction [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (stat [_ stat] "Calculating value of the stat w. modifiers"))

(defprotocol Modifiers
  (->modified-value [_ modifier-k base-value]))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity* ctx]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera/position (world-camera ctx))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (world-viewport-width ctx))  2)))
     (<= ydist (inc (/ (float (world-viewport-height ctx)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [context source* target*]
  (and (or (not (:entity/player? source*))
           (on-screen? target* context))
       (not (and los-checks?
                 (ray-blocked? context (:position source*) (:position target*))))))
