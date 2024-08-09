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
    (str "Damage based on entity strength."
         (when source
           (str "\n" (effect/text (damage-effect effect-ctx)
                                  effect-ctx)))))

  (effect/valid-params? [_ effect-ctx]
    (effect/valid-params? (damage-effect effect-ctx)))

  (effect/txs [_ effect-ctx]
    (effect/txs (damage-effect effect-ctx) effect-ctx)))
