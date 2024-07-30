(ns cdq.tx.melee-damage
  (:require [core.component :as component]
            [cdq.api.effect :as effect]
            [cdq.api.context :refer [transact!]]))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (:stats/strength (:entity/stats entity*))
                     0)]
    {:damage/min-max [strength strength]}))

(component/def :tx/melee-damage {}
  _
  (effect/text [_ {:keys [effect/source] :as ctx}]
    (if source
      (effect/text [:tx/damage (entity*->melee-damage @source)] ctx)
      "Damage based on entity stats."))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (transact! [_ {:keys [effect/source]}]
    [[:tx/damage (entity*->melee-damage @source)]]))
