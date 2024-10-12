(ns core.data)

(defn definition->type [data]
  (if (vector? data) (data 0) data))

(defmulti schema
  "Returns the schema for the data-definition."
  definition->type)

(defmethod schema :default [data] data)

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :enum [[_ items]]
  (apply vector :enum items))
