(ns api.modifier
  (:require [clojure.string :as str]
            [clojure.math :as math]))

; TODO in case of damage receive negative values are positive .... so green ... o.o
(defn- +? [n]
  (case (math/signum n)
    (0.0 1.0) "[LIME]+"
    -1.0 "[SCARLET]"))

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
