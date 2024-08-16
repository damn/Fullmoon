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
