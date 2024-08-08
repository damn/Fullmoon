(ns effect.melee-damage
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (:stats/strength (:entity/stats entity*))
                     0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect [{:keys [effect/source]}]
  [:effect/damage (entity*->melee-damage @source)])

(defcomponent :effect/melee-damage {}
  (effect/text [_ {:keys [effect/source] :as effect-ctx}]
    (if source
      (effect/text (damage-effect effect-ctx) effect-ctx)
      "Damage based on entity stats."))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (effect/txs [_ effect-ctx]
    (effect/txs (damage-effect effect-ctx) effect-ctx)))
