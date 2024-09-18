(ns core.val-max
  (:require [clojure.string :as str]
            [malli.core :as m]
            [core.component :refer [defcomponent]]
            [core.operation :as operation]))

(def ^:private val-max-schema
  (m/schema [:and
             [:vector {:min 2 :max 2} [:int {:min 0}]]
             [:fn {:error/fn (fn [{[^int v ^int mx] :value} _]
                               (when (< mx v)
                                 (format "Expected max (%d) to be smaller than val (%d)" v mx)))}
              (fn [[^int a ^int b]] (<= a b))]]))

(defn val-max-ratio
  "If mx and v is 0, returns 0, otherwise (/ v mx)"
  [[^int v ^int mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  (if (and (zero? v) (zero? mx))
    0
    (/ v mx)))

#_(defn lower-than-max? [[^int v ^int mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  (< v mx))

#_(defn set-to-max [[v mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  [mx mx])

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (let [[val-or-max inc-or-mult] (mapv keyword (str/split (name op-k) #"-"))]
    [val-or-max (keyword "op" (name inc-or-mult))]))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :op/inc])
 )

(defcomponent :op/val-max
  (operation/value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (operation/value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (operation/apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(operation/apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (operation/order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (operation/order [op-k value]))))

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
  (= (operation/apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (operation/apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (operation/apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (operation/apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (operation/apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (operation/apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )

(defcomponent :val-max {:widget :number :schema (m/form val-max-schema)})
