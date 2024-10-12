(ns core.data
  (:refer-clojure :exclude [type]))

(defn type [data]
  (if (vector? data)
    (data 0)
    data))

(defmulti schema type)
(defmethod schema :default [data] data)

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
   [:frames :some]
   [:frame-duration pos?]
   [:looping? :boolean]])
