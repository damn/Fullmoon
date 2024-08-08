(ns effect.stun
  (:require [core.component :refer [defcomponent]]
            [utils.core :refer [readable-number]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]
            [core.data :as data]))

(defcomponent :effect/stun data/pos-attr
  (effect/text [[_ _effect-ctx duration]]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/valid-params? [[_ {:keys [effect/source effect/target]}]]
    (and target))

  (transact! [[_ {:keys [effect/target]} duration]]
    [[:tx/event target :stun duration]]))
