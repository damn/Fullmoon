(ns api.effect
  (:require [core.component :refer [defsystem]]))

(defsystem valid-params? [_ effect-ctx])
(defmethod valid-params? :default [_ effect-ctx] true)

(defsystem text          [_ effect-ctx])
(defmethod text :default [_ effect-ctx])

(defsystem txs [_ effect-ctx])

(defsystem useful?       [_ effect-ctx ctx]) ; only used @ AI ??
(defmethod useful? :default [_ effect-ctx ctx] true)

(defsystem render-info   [_ effect-ctx g])
(defmethod render-info :default [_ effect-ctx g])
