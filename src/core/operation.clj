(ns core.operation
  (:require [clojure.math :as math]
            [core.component :as component]))

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn info-text [{value 1 :as operation}]
  (str (+? value) (component/value-text operation)))
