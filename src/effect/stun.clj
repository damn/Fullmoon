(ns effect.stun
  (:require [core.component :refer [defcomponent]]
            [utils.core :refer [readable-number]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]
            [core.data :as data]))

(defcomponent :effect/stun data/pos-attr
  (effect/text [[_ duration] _ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target))

  (transact! [[_ duration] {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))
