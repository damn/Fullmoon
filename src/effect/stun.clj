(ns effect.stun
  (:require [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

(defcomponent :effect/stun data/pos-attr
  (effect/text [[_ duration] _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target))

  (transact! [[_ duration] {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))
