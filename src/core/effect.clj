(ns core.effect
  (:require [core.component :refer [defsystem]]))

(defsystem applicable?
  "An effect will only be done (with component/do!) if this function returns truthy.
Required system for every effect, no default."
  [_ ctx])

(defsystem useful?
  "Used for NPC AI.
Called only if applicable? is truthy.
For example use for healing effect is only useful if hitpoints is < max.
Default method returns true."
  [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render "Renders effect during active-skill state while active till done?. Default do nothing." [_ g ctx])
(defmethod render :default [_ g ctx])
