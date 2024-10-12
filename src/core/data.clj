(ns core.data
  (:refer-clojure :exclude [type])
  (:require [clojure.gdx.utils :refer [safe-get]]
            [core.component :as component]))

(defn type [data]
  (if (vector? data)
    (data 0)
    data))

(defmulti edn->value (fn [data v] (type data)))
(defmethod edn->value :default [_data v] v)

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

(defn component [k]
  (:data (safe-get component/attributes k)))

(defn- attribute-schema
  "Can define keys as just keywords or with schema-props like [:foo {:optional true}]."
  [ks]
  (for [k ks
        :let [k? (keyword? k)
              schema-props (if k? nil (k 1))
              k (if k? k (k 0))]]
    (do
     (assert (keyword? k))
     (assert (or (nil? schema-props) (map? schema-props)) (pr-str ks))
     [k schema-props (schema (component k))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defmethod schema :map [[_ ks]]
  (map-schema ks))

(defmethod schema :map-optional [[_ ks]]
  (map-schema (map (fn [k] [k {:optional true}]) ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/attributes)))

(defmethod schema :components-ns [[_ ns-name-k]]
  (schema [:map-optional (namespaced-ks ns-name-k)]))
