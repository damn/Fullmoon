(ns context.game-paused
  (:require [core.component :refer [defcomponent] :as component]))

(defcomponent :context/game-paused {}
  (component/create [_ _ctx]
    (atom nil)))
