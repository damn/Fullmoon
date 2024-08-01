(ns context.game-logic-frame
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/game-logic-frame {}
  _
  (ctx/create [_ _ctx] (atom 0)))
