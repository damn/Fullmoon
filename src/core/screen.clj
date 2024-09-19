(ns core.screen
  (:require [core.component :refer [defsystem]]))

(defsystem render! "FIXME" [_ app-state])

(defsystem render "FIXME" [_ ctx])
(defmethod render :default [_ ctx]
  ctx)
