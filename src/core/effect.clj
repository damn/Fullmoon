(ns core.effect
  (:require [core.component :refer [defsystem]]))

(defsystem applicable? [_ ctx])

(defsystem useful? [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render [_ g ctx])
(defmethod render :default [_ g ctx])
