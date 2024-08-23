(ns components.entity.projectile-collision
  (:require [core.component :as component :refer [defcomponent]]
            [utils.core :refer [find-first]]
            [core.context :as ctx :refer [world-grid]]
            [core.entity :as entity]
            [core.world.grid :refer [rectangle->cells]]
            [core.world.cell :as cell :refer [cells->entities]]))

(defcomponent :entity/projectile-collision {}
  {:keys [hit-effects already-hit-bodies piercing?]}
  (component/create [[_ v] _ctx]
    (assoc v :already-hit-bodies #{}))

  ; TODO add proper effect-ctx here for effect-ctx/text
  ; TODO DRY! LIME color for effects ...
  (component/info-text [_ ctx]
    (str (when piercing? "[GRAY]Piercing[]\n")
         "[LIME]" (ctx/effect-text ctx hit-effects) "[]"))

  ; TODO probably belongs to body
  (entity/tick [[k _] entity ctx]
    ; TODO this could be called from body on collision
    ; for non-solid
    ; means non colliding with other entities
    ; but still collding with other stuff here ? o.o
    (let [entity* @entity
          cells* (map deref (rectangle->cells (world-grid ctx) entity*)) ; just use cached-touched -cells
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*) ; this is not clear in the componentname & what if they dont have faction - ??
                                             (:entity/faction @%))
                                       (:collides? @%)
                                       (entity/collides? entity* @%))
                                 (cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(cell/blocked? % (:z-order entity*)) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:tx.entity/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)]) ; this is only necessary in case of not piercing ...
       (when destroy?
         [:tx/destroy id])
       (when hit-entity
         [:tx/effect {:effect/source id :effect/target hit-entity} hit-effects])])))
