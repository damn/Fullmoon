(ns core.state
  (:require [core.component :refer [defsystem]]))

(defsystem enter [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  [_ ctx])
(defmethod exit :default  [_ ctx])

;; Player-State

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
