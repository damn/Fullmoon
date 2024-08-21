(ns api.entity
  (:require [math.vector :as v]
            [core.component :refer [defsystem]]
            [utils.core :as utils]))

(defrecord Entity [position
                   left-bottom
                   width
                   height
                   half-width
                   half-height
                   radius
                   solid?
                   z-order
                   rotation-angle])

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
(def min-solid-body-size 0.39) ; == spider smallest creature size.

(def z-orders [:z-order/on-ground
               :z-order/ground
               :z-order/flying
               :z-order/effect])

(def render-order (utils/define-order z-orders))

(defn ->Body [{[x y] :position
               :keys [position
                      width
                      height
                      solid?
                      z-order
                      rotation-angle]}]
  (assert position)
  (assert width)
  (assert height)
  (assert (>= width  (if solid? min-solid-body-size 0)))
  (assert (>= height (if solid? min-solid-body-size 0)))
  (assert (or (nil? solid?) (boolean? solid?)))
  (assert ((set z-orders) z-order))
  (assert (not (and (#{:z-order/effect :z-order/on-ground} z-order) solid?)))
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
    :solid? solid?
    :z-order z-order
    :rotation-angle (or rotation-angle 0)}))

(defsystem create [_ entity ctx])
(defmethod create :default [_ entity ctx])

(defsystem destroy [_ entity ctx])
(defmethod destroy :default [_ entity ctx])

(defsystem tick [_ entity ctx])
(defmethod tick :default [_ entity ctx])

; => simply body/render ???
(defsystem render-below   [_ entity* g ctx])
(defsystem render-default [_ entity* g ctx])
(defsystem render-above   [_ entity* g ctx])
(defsystem render-info    [_ entity* g ctx])

(def render-systems [render-below
                     render-default
                     render-above
                     render-info])

(defn tile [entity*]
  (utils/->tile (:position entity*)))

(defn direction [entity* other-entity*]
  (v/direction (:position entity*) (:position other-entity*)))

; TODO add v/distance ...

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
