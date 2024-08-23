(ns core.entity-state
  (:require [core.component :refer [defsystem]]))

(defsystem enter [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem tick  [_ ctx])
(defmethod tick :default  [_ ctx])

(defsystem render-below [_ entity* g ctx])
(defmethod render-below :default [_ entity* g ctx])

(defsystem render-above [_ entity* g ctx])
(defmethod render-above :default [_ entity* g ctx])

(defsystem render-info  [_ entity* g ctx])
(defmethod render-info :default  [_ entity* g ctx])

; Player
(defsystem player-enter [_])
(defmethod player-enter :default [_])

(defsystem pause-game? [_])
(defmethod pause-game? :default [_])

(defsystem manual-tick [_ ctx])
(defmethod manual-tick :default [_ ctx])

(defsystem clicked-inventory-cell [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defsystem clicked-skillmenu-skill [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])
