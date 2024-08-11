(ns entity.body
  (:require [math.vector :as v]
            [utils.core :refer [->tile]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :as ctx]
            [api.entity :as entity]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.tx :refer [transact!]]
            [api.world.grid :refer [valid-position?]]))

; # :z-order/flying has no effect for now

; * entities with :z-order/flying are not flying over water,etc. (movement/air)
; because using potential-field for z-order/ground
; -> would have to add one more potential-field for each faction for z-order/flying
; * they would also (maybe) need a separate occupied-cells if they don't collide with other
; * they could also go over ground units and not collide with them
; ( a test showed then flying OVER player entity )
; -> so no flying units for now

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
(def ^:private min-solid-body-size 0.4)

; set max speed so small entities are not skipped by projectiles
; could set faster than max-speed if I just do multiple smaller movement steps in one frame
(defn- max-speed [ctx]
  (/ min-solid-body-size (ctx/max-delta-time ctx)))

; for adding speed multiplier modifier -> need to take max-speed into account!
(defn- update-position [entity* delta {:keys [direction speed]}] ; TODO no need entity* just pass body here....
  (let [apply-delta (fn [position]
                      (mapv #(+ %1 (* %2 speed delta)) position direction))]
    (-> entity*
        (update-in [:entity/body :position   ] apply-delta)
        (update-in [:entity/body :left-bottom] apply-delta))))

(defn- update-position-non-solid [ctx entity* movement]
  (update-position entity* (ctx/delta-time ctx) movement))

; TODO just take body -> make valid-position also take body only ....
; => flying/z-order into body....
(defn- try-move [ctx entity* movement]
  (let [entity* (update-position entity* (ctx/delta-time ctx) movement)]
    (when (valid-position? (ctx/world-grid ctx) entity*) ; TODO call on ctx shortcut fn
      entity*)))

; TODO sliding threshold
; TODO name - with-sliding? 'on'
; TODO if direction was [-1 0] and invalid-position then this algorithm tried to move with
; direection [0 0] which is a waste of processor power...
(defn- update-position-solid [ctx entity* {[vx vy] :direction :as movement}]
  (let [xdir (Math/signum (float vx))
        ydir (Math/signum (float vy))]
    (or (try-move ctx entity* movement)
        (try-move ctx entity* (assoc movement :direction [xdir 0]))
        (try-move ctx entity* (assoc movement :direction [0 ydir])))))

(def ^:private show-body-bounds false)

(defn- draw-bounds [g {[x y] :left-bottom :keys [width height solid?]}]
  (when show-body-bounds
    (g/draw-rectangle g x y width height (if solid? color/white color/gray))))

; TODO I cannot dissoc any key then I lose the record!
; check somehow that we get a proper body class always and dont destroy it into a plain map?
(defrecord Body [position
                 left-bottom
                 width
                 height
                 half-width
                 half-height
                 radius
                 solid?
                 z-order
                 rotation-angle
                 movement
                 rotate-in-movement-direction?
                 touched-cells
                 occupied-cells])

; TODO how 2 do default values,its not default-values , its non-optional attributes !
; similar to components nested-map
;:default-value {:width 0.5 :height 0.5 :solid? true}
; TODO label == not editable
; TODO just defattribute ? warn on overwrite add there !
(defcomponent :width  {:widget :label :schema pos?}) ; TODO make px
(defcomponent :height {:widget :label :schema pos?}) ; TODO make px
(defcomponent :solid? {:widget :label :schema boolean?})

; TODO body assert >+ min body size @ properties !
; TODO this is actually property/entity .. or creature/entity ....
; nothing to do with the schema of this component ....
; :z-order (apply data/enum entity/z-orders)
(defcomponent :entity/body (data/map-attribute :width :height :solid?)
  (entity/create-component [[_
                             {[x y] :position
                              :keys [position
                                     width
                                     height
                                     solid?
                                     z-order
                                     rotation-angle
                                     rotate-in-movement-direction?
                                     movement]}]
                            _entity*
                            _ctx]
    (assert position)
    (assert width)
    (assert height)
    (assert (>= width  (if solid? min-solid-body-size 0)))
    (assert (>= height (if solid? min-solid-body-size 0)))
    (assert (or (nil? solid?) (boolean? solid?)))
    (assert ((set entity/z-orders) z-order))
    (assert (not (and (#{:z-order/effect :z-order/on-ground} z-order) solid?)))
    (assert (or (nil? rotation-angle)
                (<= 0 rotation-angle 360)))
    (map->Body
     ; TODO position/left-bottom call to float & at movement too ?
     ; I am sure we have float conversions happening there .... at collision etc.
     {:position position
      :left-bottom [(- x (/ width  2))
                    (- y (/ height 2))]
      :width  (float width)
      :height (float height)
      :half-width  (float (/ width  2))
      :half-height (float (/ height 2))
      :radius (float (max (/ width  2)
                          (/ height 2)))
      :solid? solid?
      :z-order z-order
      :rotation-angle (or rotation-angle 0)
      :rotate-in-movement-direction? rotate-in-movement-direction?
      :movement movement}))

  (entity/create [_ {:keys [entity/id]} ctx]
    [[:tx/add-to-world id]])

  (entity/destroy [_ {:keys [entity/id]} ctx]
    [[:tx/remove-from-world id]])

  (entity/render-debug [[_ body] _entity* g _ctx]
    (draw-bounds g body))

  (entity/tick [[_ body] entity* ctx]
    ; TODO speed schema, what if nil/0 ? skip ... ?
    (when-let [{:keys [direction speed] :as movement} (:movement body)]
      (assert (<= speed (max-speed ctx)))
      (assert (or (zero? (v/length direction))
                  (v/normalised? direction)))
      (when-not (or (zero? (v/length direction))
                    (nil? speed)
                    (zero? speed))
        (when-let [{:keys [entity/id
                           entity/body]} (if (:solid? body)
                                           (update-position-solid     ctx entity* movement)
                                           (update-position-non-solid ctx entity* movement))]
          [[:tx.entity/assoc-in id [:entity/body :position   ] (:position    body)]
           [:tx.entity/assoc-in id [:entity/body :left-bottom] (:left-bottom body)]
           (when (:rotate-in-movement-direction? body)
             [:tx.entity/assoc-in id [:entity/body :rotation-angle] (v/get-angle-from-vector direction)])
           [:tx/position-changed id]])))))

(extend-type api.entity.Entity
  entity/Body
  (position [entity*] (:position (:entity/body entity*)))
  (z-order  [entity*] (:z-order  (:entity/body entity*)))

  (tile [entity*]
    (->tile (entity/position entity*)))

  (direction [entity* other-entity*]
    (v/direction (entity/position entity*) (entity/position other-entity*))))

(defmethod transact! :tx.entity/set-movement [[_ entity movement] ctx]
  {:pre [(or (nil? movement)
             (and (:direction movement) ; continue schema of that ...
                  #_(:speed movement)))]} ; princess no stats/movement-speed, then nil and here assertion-error
  [[:tx.entity/assoc-in entity [:entity/body :movement] movement]])

; add to api: ( don't access :entity/body keyword directly , so I can change to defrecord entity later (:body ) or watever)

; v/distance used with 2 positions of 2 entities a few times -> ? w. body bounds
; radius
; width, half-width, half-height
; rotation-angle ( ? )
; touched-cells (:entity/projectile-collision calls rectangle->cells )
; collides? / solid?
; entity/collides? entity*-a entity*-b

; add components:
; z-order/flying/?

; also for performance maybe later
; not Cell entities but Cell colliding/solid-entities ....
; all effects/etc. now set touched-cells which is mad.
