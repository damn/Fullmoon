(ns ^:no-doc core.effect.core
  (:require [core.math.vector :as v]
            [core.effect :as effect]
            [core.entity :as entity]
            [core.graphics.views :refer [world-mouse-position]]
            [core.component :as component :refer [defcomponent]]
            [core.ctx.mouseover-entity :as mouseover]
            [core.ctx.grid :as grid]))

(defn- nearest-enemy [{:keys [context/grid]} entity*]
  (grid/nearest-entity @(grid (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn ->npc-effect-ctx [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (entity/line-of-sight? ctx entity* @target))
                 target)]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target (entity/direction entity* @target))}))

(defn ->player-effect-ctx [ctx entity*]
  (let [target* (mouseover/entity* ctx)
        target-position (or (and target* (:position target*))
                            (world-mouse-position ctx))]
    {:effect/source (:entity/id entity*)
     :effect/target (:entity/id target*)
     :effect/target-position target-position
     :effect/direction (v/direction (:position entity*) target-position)}))

; SCHEMA effect-ctx
; * source = always available
; # npc:
;   * target = maybe
;   * direction = maybe
; # player
;  * target = maybe
;  * target-position  = always available (mouse world position)
;  * direction  = always available (from mouse world position)

(defcomponent :tx/effect
  (component/do! [[_ effect-ctx effects] ctx]
    (-> ctx
        (merge effect-ctx)
        (effect/do! (filter #(component/applicable? % effect-ctx) effects))
        ; TODO
        ; context/source ?
        ; skill.context ?  ?
        ; generic context ?( projectile hit is not skill context)
        (dissoc :effect/source
                :effect/target
                :effect/direction
                :effect/target-position))))

; would have to do this only if effect even needs target ... ?
(defn- check-remove-target [{:keys [effect/source] :as ctx}]
  (update ctx :effect/target (fn [target]
                               (when (and target
                                          (not (:entity/destroyed? @target))
                                          (entity/line-of-sight? ctx @source @target))
                                 target))))

(defn applicable? [ctx effects]
  (let [ctx (check-remove-target ctx)]
    (some #(component/applicable? % ctx) effects)))

(defn- mana-value [entity*]
  (if-let [mana (entity/stat entity* :stats/mana)]
    (mana 0)
    0))

(defn- not-enough-mana? [entity* {:keys [skill/cost]}]
  (> cost (mana-value entity*)))

(defn skill-usable-state
  [ctx entity* {:keys [skill/cooling-down? skill/effects] :as skill}]
  (cond
   cooling-down?
   :cooldown

   (not-enough-mana? entity* skill)
   :not-enough-mana

   (not (applicable? ctx effects))
   :invalid-params

   :else
   :usable))
