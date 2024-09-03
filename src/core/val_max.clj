(ns core.val-max
  (:require [clojure.string :as str]
            [malli.core :as m]
            [core.component :as component :refer [defcomponent]]))

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
  (component/value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (component/value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (component/apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(component/apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (component/order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (component/order [op-k value]))))

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
  (= (component/apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (component/apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (component/apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (component/apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (component/apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (component/apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )

; TODO gets lost here ....
(defcomponent :val-max {:widget :number :schema (m/form val-max-schema)})
