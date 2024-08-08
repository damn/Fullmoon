(ns entity.projectile-collision
  (:require [core.component :refer [defcomponent]]
            [math.geom :as geom]
            [utils.core :refer [find-first]]
            [api.context :refer [world-grid]]
            [api.entity :as entity]
            [api.world.grid :refer [rectangle->cells]]
            [api.world.cell :as cell :refer [cells->entities]]))

(defcomponent :entity/projectile-collision {}
  (entity/create-component [[_ v] _components _ctx]
    (assoc v :already-hit-bodies #{}))

  (entity/tick [[k {:keys [hit-effect
                           already-hit-bodies
                           piercing?]}]
                entity*
                ctx]
    (let [cells* (map deref (rectangle->cells (world-grid ctx) (:entity/body entity*))) ; just use touched -cells
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*) ; this is not clear in the componentname & what if they dont have faction - ??
                                             (:entity/faction @%))
                                       (:solid? (:entity/body @%)) ; solid means -- collides? -> can call it collides? then ?
                                       (geom/collides? (:entity/body entity*)
                                                       (:entity/body @%)))
                                 (cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(cell/blocked? % entity*) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:tx.entity/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)]) ; this is only necessary in case of not piercing ...
       (when destroy?
         [:tx/destroy id])
       (when hit-entity
         (effect-ctx/txs {:effect/source id :effect/target hit-entity} hit-effect))]))) ; TODO concat ?
