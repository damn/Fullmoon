(ns core.entity.projectile-collision
  (:require [utils.core :refer [find-first]]
            [core.component :as component :refer [defcomponent]]
            [core.world :refer [world-grid]]
            [core.entity :as entity]
            [core.grid :as grid]))

(defcomponent :entity/projectile-collision
  {:let {:keys [entity-effects already-hit-bodies piercing?]}}
  (component/create [[_ v] _ctx]
    (assoc v :already-hit-bodies #{}))

  ; TODO probably belongs to body
  (entity/tick [[k _] entity ctx]
    ; TODO this could be called from body on collision
    ; for non-solid
    ; means non colliding with other entities
    ; but still collding with other stuff here ? o.o
    (let [entity* @entity
          cells* (map deref (grid/rectangle->cells (world-grid ctx) entity*)) ; just use cached-touched -cells
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*) ; this is not clear in the componentname & what if they dont have faction - ??
                                             (:entity/faction @%))
                                       (:collides? @%)
                                       (entity/collides? entity* @%))
                                 (grid/cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(grid/blocked? % (:z-order entity*)) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:tx/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)]) ; this is only necessary in case of not piercing ...
       (when destroy?
         [:tx/destroy id])
       (when hit-entity
         [:tx/effect {:effect/source id :effect/target hit-entity} entity-effects])])))
