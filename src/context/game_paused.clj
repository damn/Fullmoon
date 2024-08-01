(ns context.game-paused
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/game-paused {}
  _
  (ctx/create [_ _ctx] (atom nil)))
