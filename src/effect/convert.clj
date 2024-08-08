(ns effect.convert
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [core.data :as data]))

(defcomponent :effect/convert data/boolean-attr
  (effect/text [_]
    "Converts target to your side.")

  (effect/valid-params? [[_ {:keys [effect/source effect/target]}]]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (transact! [[_ {:keys [effect/source effect/target]}]]
    [[:tx.entity/assoc target :entity/faction (entity/friendly-faction @source)]]))
