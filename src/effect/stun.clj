(ns effect.stun
  (:require [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]))

(defcomponent :effect/stun data/pos-attr
  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target))

  (effect/text [[_ duration] _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/txs [[_ duration] {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))
