(ns core.effect
  (:require [core.component :refer [defsystem]]))

(defsystem applicable? "? TODO. Required system, no default."[_ ctx])

(defsystem useful? "Used for AI. Default true. "[_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render "? renders effect while active till done?. Default do nothing." [_ g ctx])
(defmethod render :default [_ g ctx])
