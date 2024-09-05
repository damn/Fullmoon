(ns components.entity.movement
  (:require [malli.core :as m]
            [math.vector :as v]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.world.cell :as cell]
            [core.world.grid :as world-grid]))

(defn- move-position [position {:keys [direction speed delta-time]}]
  (mapv #(+ %1 (* %2 speed delta-time)) position direction))

(defn- move-body [body movement]
  (-> body
      (update :position    move-position movement)
      (update :left-bottom move-position movement)))

(defn- valid-position? [grid body]
  {:pre [(:collides? body)]}
  (let [{:keys [entity/id z-order _collides?]} body
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

(defcomponent :entity/movement
  {:let {:keys [direction speed rotate-in-movement-direction?] :as movement}}
  (component/tick [_ eid ctx]
    (assert (m/validate entity/movement-speed-schema speed))
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
          [[:tx/assoc eid :position    (:position    body)]
           [:tx/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:tx/assoc eid :rotation-angle (v/get-angle-from-vector direction)])
           [:tx/position-changed eid]])))))

(defcomponent :tx/set-movement
  (component/do! [[_ entity movement] ctx]
    (assert (or (nil? movement)
                (nil? (:direction movement))
                (and (:direction movement) ; continue schema of that ...
                     #_(:speed movement)))) ; princess no stats/movement-speed, then nil and here assertion-error
    [(if (or (nil? movement)
             (nil? (:direction movement)))
       [:tx/dissoc entity :entity/movement]
       [:tx/assoc entity :entity/movement movement])]))

; TODO add teleport effect ~ or tx
