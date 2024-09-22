(ns core.screen
  (:require [core.component :refer [defsystem]]))

(defsystem enter "FIXME" [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  "FIXME" [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem render! "FIXME" [_ app-state])

(defsystem render "FIXME" [_ ctx])
(defmethod render :default [_ ctx]
  ctx)
