(ns entity.movement
  (:require [malli.core :as m]
            [math.vector :as v]
            [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.entity :as entity]
            [api.effect :as effect]
            [api.world.cell :as cell]
            [api.world.grid :as world-grid]))

; # :z-order/flying has no effect for now

; * entities with :z-order/flying are not flying over water,etc. (movement/air)
; because using potential-field for z-order/ground
; -> would have to add one more potential-field for each faction for z-order/flying
; * they would also (maybe) need a separate occupied-cells if they don't collide with other
; * they could also go over ground units and not collide with them
; ( a test showed then flying OVER player entity )
; -> so no flying units for now

; set max speed so small entities are not skipped by projectiles
; could set faster than max-speed if I just do multiple smaller movement steps in one frame
(def max-speed (/ entity/min-solid-body-size
                  ctx/max-delta-time))

(def movement-speed-schema [:and number? [:>= 0] [:<= max-speed]])
(def ^:private movement-speed-schema* (m/schema movement-speed-schema))

(defn- move-position [position {:keys [direction speed delta-time]}]
  (mapv #(+ %1 (* %2 speed delta-time)) position direction))

(defn- move-body [body movement]
  (-> body
      (update :position    move-position movement)
      (update :left-bottom move-position movement)))

(defn- valid-position? [grid body]
  {:pre [(:collides? body)]}
  (let [{:keys [entity/id z-order collides?]} body
        cells* (into [] (map deref) (world-grid/rectangle->cells grid body))]
    (and (not-any? #(cell/blocked? % z-order) cells*)
         (->> cells*
              cell/cells->entities
              (not-any? (fn [other-entity]
                          (let [other-entity* @other-entity]
                            (and (not= (:entity/id other-entity*) id)
                                 (:collides? other-entity*)
                                 (entity/collides? other-entity* body)))))))))

(defn- try-move [grid body movement]
  (let [new-body (move-body body movement)]
    (when (valid-position? grid new-body)
      new-body)))

; TODO sliding threshold
; TODO name - with-sliding? 'on'
; TODO if direction was [-1 0] and invalid-position then this algorithm tried to move with
; direection [0 0] which is a waste of processor power...
(defn- try-move-solid-body [grid body {[vx vy] :direction :as movement}]
  (let [xdir (Math/signum (float vx))
        ydir (Math/signum (float vy))]
    (or (try-move grid body movement)
        (try-move grid body (assoc movement :direction [xdir 0]))
        (try-move grid body (assoc movement :direction [0 ydir])))))

(defcomponent :entity/movement {}
  (entity/tick [[_ {:keys [direction speed rotate-in-movement-direction?] :as movement}] eid ctx]
    (assert (m/validate movement-speed-schema* speed))
    (assert (or (zero? (v/length direction))
                (v/normalised? direction)))
    (when-not (or (zero? (v/length direction))
                  (nil? speed)
                  (zero? speed))
      (let [movement (assoc movement :delta-time (ctx/delta-time ctx))
            body @eid]
        (when-let [body (if (:collides? body) ; < == means this is a movement-type ... which could be a multimethod ....
                          (try-move-solid-body (ctx/world-grid ctx) body movement)
                          (move-body body movement))]
          [[:tx.entity/assoc eid :position    (:position    body)]
           [:tx.entity/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:tx.entity/assoc eid :rotation-angle (v/get-angle-from-vector direction)])
           [:tx/position-changed eid]])))))

(defcomponent :tx.entity/set-movement {}
  (effect/do! [[_ entity movement] ctx]
    {:pre [(or (nil? movement)
               (and (:direction movement) ; continue schema of that ...
                    #_(:speed movement)))]} ; princess no stats/movement-speed, then nil and here assertion-error
    [[:tx.entity/assoc entity :entity/movement movement]]))

; TODO add teleport effect ~ or tx
