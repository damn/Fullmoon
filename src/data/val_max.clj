(ns data.val-max
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

(defn- ->max-zero-int [v]
  (-> v int (max 0)))

(defn apply-val-max-modifier [val-max [[val-or-max inc-or-mult] values]]
  {:post [(m/validate val-max-schema %)]}
  (assert (m/validate val-max-schema val-max)
          (str "Invalid val-max-schema: " val-max))
  ; TODO similar ops / as in stats ....
  ; move there ??
  (let [f (case inc-or-mult
            :inc #(reduce + % values)
            :mult #(* % (reduce + 1 values)))
        [v mx] ((case val-or-max
                  :val (fn [[v mx] f] [(f v) mx])
                  :max (fn [[v mx] f] [v (f mx)]))
                val-max
                f)
        v  (->max-zero-int v)
        mx (->max-zero-int mx)]
    (case val-or-max
      :val [v (max v mx)]
      :max [(min v mx) mx])))

(comment
 (= (apply-val-max-modifier [5 10]
                            [[:val :inc] [30]])
    [35 35]
    )
 (= (apply-val-max-modifier [5 10]
                            [[:max :mult] [-0.5]])
    [5 5])


 )

#_(defn- inc<mult [[[val-or-max inc-or-mult] _values]]
  (case inc-or-mult
    :inc 0
    :mult 1))

#_(defn apply-val-max-modifiers
  "First inc then mult"
  [val-max modifiers]
  {:pre [(m/validate val-max-schema val-max)]
   :post [(m/validate val-max-schema %)]}
  (reduce apply-val-max-modifier
          val-max
          (sort-by inc<mult modifiers)))

(comment
 (and
  (= (apply-val-max-modifiers [5 10]
                              {[:max :mult] [2]
                               [:val :mult] [1.5]
                               [:val :inc] [1 2]
                               [:max :inc] [1]})
     [20 33])

  (= (apply-val-max-modifiers [5 10]
                              {[:max :mult] [2]
                               [:val :mult] [1.5]
                               [:val :inc] [1]
                               [:max :inc] [1]})
     [15 33])

  (= (apply-val-max-modifiers [9 22]
                              {[:max :mult] [0.7]
                               [:val :mult] [1]
                               [:val :inc] [-2]
                               [:max :inc] [0]})
     [14 37]))
 )
