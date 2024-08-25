(ns components.operation.numbers
  (:require [core.component :as component :refer [defcomponent]]))

(defcomponent :op/inc
  {:schema :number?
   :let value}
  (component/value-text [_] (str value))
  (component/apply [_ base-value] (+ base-value value))
  (component/order [_] 0))

(defcomponent :op/mult
  {:schema :number?
   :let value}
  (component/value-text [_] (str (int (* 100 value)) "%"))
  (component/apply [_ base-value] (* base-value (inc value)))
  (component/order [_] 1))
