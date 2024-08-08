(ns effect.melee-damage
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (:stats/strength (:entity/stats entity*))
                     0)]
    {:damage/min-max [strength strength]}))

(defcomponent :effect/melee-damage {}
  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (effect/text [_ {:keys [effect/source] :as effect-ctx}]
    (if source
      (effect/text [:effect/damage effect-ctx (entity*->melee-damage @source)]) ; TODO DRY with the transact! fn ...
      "Damage based on entity stats."))

  (effect/txs [_ {:keys [effect/source]}]
    [[:effect/damage (entity*->melee-damage @source)]]))
