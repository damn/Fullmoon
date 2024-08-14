(ns effect.convert
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

(defcomponent :effect/convert data/boolean-attr
  (effect/text [_ _effect-ctx]
    "Converts target to your side.")

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (transact! [_ {:keys [effect/source effect/target]}]
    [[:tx.entity/assoc target :entity/faction (entity/friendly-faction @source)]]))
