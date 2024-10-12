(ns core.data
  (:refer-clojure :exclude [type]))

(defn type [data]
  {:post [(keyword? %)]}
  (if (vector? data)
    (data 0)
    data))

(defmulti schema type)
(defmethod schema :default [data] data)

;;;;

; Do not use functions itself as data-types ( to delete the defmethods below )
; * tooltip in editor will not work for function
; * cannot derive, have to list possible data types
; Also decided not to prefix :data/ or :d/ because we pass through schema
; some malli schemas like :boolean/:string/:enum/:qualified-keyword (?)
; :map too?

;;;;

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :sound [_]
  :string)

(defmethod schema :image [_]
  [:map {:closed true}
   [:file :string]
   [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]])

(defmethod schema :data/animation [_]
  [:map {:closed true}
   [:frames :some] ; FIXME actually images
   [:frame-duration pos?]
   [:looping? :boolean]])
