(ns api.effect
  (:require [core.component :refer [defsystem]]))

(defsystem text          [_ effect-ctx])
(defmethod text :default [_ effect-ctx])

(defsystem usable?          [_ effect-ctx])
(defmethod usable? :default [_ effect-ctx] true)

(defsystem useful?          [_ effect-ctx ctx]) ; used for NPCs
(defmethod useful? :default [_ effect-ctx ctx] true)

(defsystem render-info          [_ effect-ctx g])
(defmethod render-info :default [_ effect-ctx g])
