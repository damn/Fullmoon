(ns api.modifier
  (:require [clojure.string :as str]
            [clojure.math :as math]))

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
(def ^:private positive-modifier-color "[CYAN]" #_"[LIME]")
(def ^:private negative-modifier-color "[CYAN]" #_"[SCARLET]")

(defn- +? [n]
  (case (math/signum n)
    (0.0 1.0) (str positive-modifier-color "+")
    -1.0 (str negative-modifier-color "")))

(defn- ->percent [v]
  (str (int (* 100 v)) "%"))

(defn info-text [[[stat operation] value]]
  (str (+? value)
       (case operation
         :inc (str value " ")
         :mult (str (->percent value) " ")
         [:val :inc] (str value " min ")
         [:max :inc] (str value " max ")
         [:val :mult] (str (->percent value) " min ")
         [:max :mult] (str (->percent value) " max "))
       (name stat)
       "[]"))
