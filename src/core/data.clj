(ns core.data)

(defmulti edn->value (fn [data v] (:type data)))
(defmethod edn->value :default [_data v] v)

(defn definition->type [data]
  (if (vector? data) (data 0) data))

(defmulti schema
  "Returns the schema for the data-definition."
  definition->type)

(defmethod schema :default [data] data)

(defn ->type [data]
  {:type (definition->type data)
   :schema (schema data)})

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :enum [[_ items]]
  (apply vector :enum items))
