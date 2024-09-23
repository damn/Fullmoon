(ns core.operation
  (:require [clojure.math :as math]
            [core.component :as component :refer [defsystem]]))

(defsystem value-text "FIXME" [_])
(defsystem apply "FIXME" [_ base-value])
(defsystem order "FIXME" [_])

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn info-text [{value 1 :as operation}]
  (str (+? value) (value-text operation)))

(defcomponent :op/inc
  {:data :number
   :let value}
  (value-text [_] (str value))
  (apply [_ base-value] (+ base-value value))
  (order [_] 0))

(defcomponent :op/mult
  {:data :number
   :let value}
  (value-text [_] (str (int (* 100 value)) "%"))
  (apply [_ base-value] (* base-value (inc value)))
  (order [_] 1))
