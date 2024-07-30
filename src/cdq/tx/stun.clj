(ns cdq.tx.stun
  (:require [core.component :as component]
            [utils.core :refer [readable-number]]
            [cdq.api.context :refer [transact!]]
            [cdq.api.effect :as effect]
            [cdq.attributes :as attr]))

(component/def :tx/stun attr/pos-attr
  duration
  (effect/text [_ _ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target))

  (transact! [_ {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))
