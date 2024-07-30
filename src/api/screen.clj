(ns api.screen
  (:require [core.component :as component]))

(component/defn create [_ ctx])

(defprotocol Screen
  (show   [_ context])
  (hide   [_ context])
  (render [_ context]))
