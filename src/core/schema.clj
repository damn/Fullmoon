(ns core.schema
  (:refer-clojure :exclude [type]))

(defn type [schema]
  {:post [(keyword? %)]}
  (if (vector? schema)
    (schema 0)
    schema))

(defmulti form type)
(defmethod form :default [schema] schema)

;;

(defmethod form :number  [_] number?)
(defmethod form :nat-int [_] nat-int?)
(defmethod form :int     [_] int?)
(defmethod form :pos     [_] pos?)
(defmethod form :pos-int [_] pos-int?)

(defmethod form :sound [_] :string)

(defmethod form :image [_]
  [:map {:closed true}
   [:file :string]
   [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]])

(defmethod form :data/animation [_]
  [:map {:closed true}
   [:frames :some] ; FIXME actually images
   [:frame-duration pos?]
   [:looping? :boolean]])
