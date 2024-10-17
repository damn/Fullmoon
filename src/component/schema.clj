(ns component.schema
  (:refer-clojure :exclude [type])
  (:require [component.core :as component]
            [utils.core :refer [safe-get]]))

(defn of [k]
  (:schema (safe-get component/meta k)))

(defn type [schema]
  (if (vector? schema)
    (schema 0)
    schema))

(defmulti form type)
(defmethod form :default [schema] schema)

(def form-of (comp form of))

(defn- attribute-form
  "Can define keys as just keywords or with schema-props like [:foo {:optional true}]."
  [ks]
  (for [k ks
        :let [k? (keyword? k)
              schema-props (if k? nil (k 1))
              k (if k? k (k 0))]]
    (do
     (assert (keyword? k))
     (assert (or (nil? schema-props) (map? schema-props)) (pr-str ks))
     [k schema-props (form-of k)])))

(defn- map-form [ks]
  (apply vector :map {:closed true} (attribute-form ks)))

(defmethod form :s/map [[_ ks]]
  (map-form ks))

(defmethod form :s/map-optional [[_ ks]]
  (map-form (map (fn [k] [k {:optional true}]) ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/meta)))

(defmethod form :s/components-ns [[_ ns-name-k]]
  (form [:s/map-optional (namespaced-ks ns-name-k)]))
