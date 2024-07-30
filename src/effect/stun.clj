(ns effect.stun
  (:require [core.component :as component]
            [utils.core :refer [readable-number]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]
            [core.data :as attr]))

(component/def :effect/stun attr/pos-attr
  duration
  (effect/text [_ _ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target))

  (transact! [_ {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))
