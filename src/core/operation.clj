(ns core.operation
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [malli.core :as m]
            [core.ctx :refer :all]))

(defsystem value-text "FIXME" [_])
(defsystem apply "FIXME" [_ base-value])
(defsystem order "FIXME" [_])

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn op-info-text [{value 1 :as operation}]
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

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (let [[val-or-max inc-or-mult] (mapv keyword (str/split (name op-k) #"-"))]
    [val-or-max (keyword "op" (name inc-or-mult))]))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :op/inc])
 )

(defcomponent :op/val-max
  (value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (order [op-k value]))))

(defcomponent :op/val-inc {:data :int})
(derive       :op/val-inc :op/val-max)

(defcomponent :op/val-mult {:data :number})
(derive       :op/val-mult :op/val-max)

(defcomponent :op/max-inc {:data :int})
(derive       :op/max-inc :op/val-max)

(defcomponent :op/max-mult {:data :number})
(derive       :op/max-mult :op/val-max)

(comment
 (and
  (= (apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )
