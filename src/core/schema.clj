(ns core.schema
  (:refer-clojure :exclude [type]))

(defn type [schema]
  {:post [(keyword? %)]}
  (if (vector? schema)
    (schema 0)
    schema))

(defmulti form type)
(defmethod form :default [schema] schema)
