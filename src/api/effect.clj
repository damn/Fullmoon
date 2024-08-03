(ns api.effect
  (:require [core.component :refer [defsystem]]))

(defsystem text          [_ ctx])
(defmethod text :default [_ _])

(defsystem valid-params? [_ ctx])
(defmethod valid-params? :default [_ _] true)

(defsystem useful?       [_ ctx]) ; only used @ AI ??
(defmethod useful? :default [_ _] true)

(defsystem render-info   [_ g ctx])
(defmethod render-info :default [_ _g _ctx])
