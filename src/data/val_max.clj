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
          (str "Invalid val-max-schema: " (pr-str val-max)))
  (assert (coll? values)
          (str "Values should be a coll?: " (pr-str values) ". Called with val-max: " (pr-str val-max) " , op:" (pr-str [val-or-max inc-or-mult])))
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
