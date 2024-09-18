(ns components.operation.numbers
  (:require [core.component :refer [defcomponent]]
            [core.operation :as operation]))

(defcomponent :op/inc
  {:data :number
   :let value}
  (operation/value-text [_] (str value))
  (operation/apply [_ base-value] (+ base-value value))
  (operation/order [_] 0))

(defcomponent :op/mult
  {:data :number
   :let value}
  (operation/value-text [_] (str (int (* 100 value)) "%"))
  (operation/apply [_ base-value] (* base-value (inc value)))
  (operation/order [_] 1))
