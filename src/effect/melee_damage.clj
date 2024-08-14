(ns effect.melee-damage
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (entity/stat entity* :stats/strength) 0)]
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

  (transact! [_ ctx]
    [(damage-effect ctx)]))
