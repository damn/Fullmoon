(ns core.screen
  (:require [core.component :refer [defsystem]]))

(defsystem render! [_ app-state])

(defsystem render [_ ctx])
(defmethod render :default [_ ctx]
  ctx)
