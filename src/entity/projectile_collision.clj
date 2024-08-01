(ns entity.projectile-collision
  (:require [core.component :refer [defcomponent]]
            [math.geom :as geom]
            [utils.core :refer [find-first]]
            [api.context :refer [world-grid]]
            [api.entity :as entity]
            [api.world.grid :refer [rectangle->cells]]
            [api.world.cell :as cell :refer [cells->entities]]))

(defcomponent :entity/projectile-collision {}
  {:keys [hit-effect
          already-hit-bodies
          piercing?]}
  (entity/create-component [[_ v] _components _ctx]
    (assoc v :already-hit-bodies #{}))

  (entity/tick [[k _] entity* ctx]
    (let [cells* (map deref (rectangle->cells (world-grid ctx) (:entity/body entity*)))
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*)
                                             (:entity/faction @%))
                                       (:solid? (:entity/body @%))
                                       (geom/collides? (:entity/body entity*)
                                                       (:entity/body @%)))
                                 (cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(cell/blocked? % entity*) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:tx.entity/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)])
       (when destroy?
         [:tx/destroy id])
       (when hit-entity
         [:tx/effect {:effect/source id :effect/target hit-entity} hit-effect])])))
