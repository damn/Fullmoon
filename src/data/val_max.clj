(ns data.val-max
  "val-max is a vector of 2 positive or zero integers  [value max-value]
  used for example as hitpoints [current max] or manapoints or damage
  [minimum maximum] in games
  there are 2 main functions:
  apply-val and apply-max
  which applies a function to value or max-value
  those functions make sure that val always remains smaller or equal than maximum
  for example damage [5 10] and we apply-val * 3
  will result in [10 10] damage
  where as apply-max / 3 => [3 3] damage."
  (:require [malli.core :as m]))

(def val-max-schema
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

(defn lower-than-max? [[^int v ^int mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  (< v mx))

(defn set-to-max [[v mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  [mx mx])

(defn- zero-or-pos-int [value]
  (-> value int (max 0)))

(defn apply-val [[v mx] f]
  {:pre [(m/validate val-max-schema [v mx])]
   :post [(m/validate val-max-schema %)]}
  (let [v (zero-or-pos-int (f v))]
    [(min v mx) mx]))

(defn apply-max [[^int v mx] f]
  {:pre [(m/validate val-max-schema [v mx])]
   :post [(m/validate val-max-schema %)]}
  (let [^int mx (zero-or-pos-int (f mx))]
    [(min v mx) mx]))

(comment
 (apply-val [3 5] (partial * 5))
 [5 5]
 (apply-val [3 5] (partial * -5))
 [0 5]
 (apply-max [3 5] (partial * -5))
 [0 0]
 (apply-max [3 5] (partial * 1.5))
 [3 7]
 (apply-max [3 5] (partial * 0.5))
 [2 2]
 )

; [operant operation]
(defn apply-val-max-modifier [val-max [[val-or-max inc-or-mult] value]]
  {:pre [(m/validate val-max-schema val-max)]
   :post [(m/validate val-max-schema %)]}
  (let [f (case inc-or-mult
            :inc  (partial + value) ; TODO use operation op => :+ :- :*, :set-to-max
            :mult (partial * value))]
    ((case val-or-max
       :val apply-val
       :max apply-max)
     val-max f)))

(defn- inc<mult [[[val-or-max inc-or-mult] value]]
  (case inc-or-mult
    :inc 0
    :mult 1))

; TODO validate modifiers
(defn apply-val-max-modifiers
  "First inc then mult"
  [val-max modifiers]
  {:pre [(m/validate val-max-schema val-max)]
   :post [(m/validate val-max-schema %)]}
  (reduce apply-val-max-modifier
          val-max
          (sort-by inc<mult modifiers)))

(comment
 (= (apply-val-max-modifiers
     [5 10]
     {[:max :mult] 2
      [:val :mult] 1.5
      [:val :inc] 1
      [:max :inc] 1})
    [9 22])

 (= (apply-val-max-modifiers
     [9 22]
     {[:max :mult] 0.7
      [:val :mult] 1
      [:val :inc] -2
      [:max :inc] 0})
    [7 15])
 )
