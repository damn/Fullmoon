(ns screens.world
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.screen :as screen :refer [Screen]]
            [context.world :as world]))

(defrecord SubScreen []
  Screen
  (show [_ _ctx])
  (hide [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))
  (render [_ ctx]
    (world/render ctx)))

(defcomponent :screens/world {}
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors []
                         :sub-screen (->SubScreen)})))
