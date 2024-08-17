(ns entity.projectile-collision
  (:require [core.component :refer [defcomponent]]
            [math.geom :as geom]
            [utils.core :refer [find-first]]
            [api.context :refer [world-grid]]
            [api.entity :as entity]
            [api.world.grid :refer [rectangle->cells]]
            [api.world.cell :as cell :refer [cells->entities]]
            [effect-ctx.core :as effect-ctx]))

(defcomponent :entity/projectile-collision {}
  (entity/create-component [[_ v] _components _ctx]
    (assoc v :already-hit-bodies #{}))

  ; TODO add proper effect-ctx here for effect-ctx/text
  ; TODO DRY! LIME color for effects ...
  (entity/info-text [[_ {:keys [hit-effects piercing?]}] _ctx]
    (str (when piercing? "[GRAY]Piercing[]\n")
         "[LIME]" (effect-ctx/text {} hit-effects) "[]"))

  (entity/tick [[k {:keys [hit-effects
                           already-hit-bodies
                           piercing?]}]
                entity*
                ctx]
    ; TODO this could be called from body on collision
    ; for non-solid
    ; means non colliding with other entities
    ; but still collding with other stuff here ? o.o
    (let [cells* (map deref (rectangle->cells (world-grid ctx) (:entity/body entity*))) ; just use cached-touched -cells
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*) ; this is not clear in the componentname & what if they dont have faction - ??
                                             (:entity/faction @%))
                                       (:solid? (:entity/body @%)) ; solid means -- collides? -> can call it collides? then ?
                                       (geom/collides? (:entity/body entity*)
                                                       (:entity/body @%)))
                                 (cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(cell/blocked? % (entity/z-order entity*)) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:tx.entity/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)]) ; this is only necessary in case of not piercing ...
       (when destroy?
         [:tx/destroy id])
       (when hit-entity
         [:tx/effect {:effect/source id :effect/target hit-entity} hit-effects])])))
