(ns core.screen
  "Screens can implement component/create, component/enter, component/exit and the systems defined here."
  (:require [core.component :refer [defsystem]]))

(defsystem render! [_ app-state])

(defsystem render [_ ctx])
(defmethod render :default [_ ctx]
  ctx)
