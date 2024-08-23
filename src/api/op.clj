(ns api.op
  (:refer-clojure :exclude [apply])
  (:require [clojure.math :as math]
            [core.component :refer [defsystem defcomponent]]))

(defsystem value-text [_])
(defsystem apply [_ base-value])
(defsystem order [_])

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn info-text [{value 1 :as operation}]
  (str (+? value) (value-text operation)))

(defcomponent :op/inc {:widget :text-field :schema number?}
  (value-text [[_ value]]
    (str value))

  (apply [[_ value] base-value]
    (+ base-value value))

  (order [_]
    0))

(defcomponent :op/mult {:widget :text-field :schema number?}
  (value-text [[_ value]]
    (str (int (* 100 value)) "%"))

  (apply [[_ value] base-value]
    (* base-value (inc value)))

  (order [_]
    1))
