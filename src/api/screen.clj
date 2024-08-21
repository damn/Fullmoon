(ns api.screen
  (:require [core.component :refer [defsystem]]))

(defsystem create [_ ctx])

(defprotocol Screen
  (show   [_ context])
  (hide   [_ context])
  (render! [_ app-state])
  (render [_ context]))
