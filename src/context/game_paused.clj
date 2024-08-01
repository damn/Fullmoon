(ns context.game-paused
  (:require [core.component :as component]))

(component/def :context/game-paused {}
  _
  (component/create [_ _ctx] (atom nil)))
