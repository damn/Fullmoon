(ns core.data
  (:refer-clojure :exclude [type])
  (:require [component.core :as component]
            [utils.core :refer [safe-get]]))

(defn component [k]
  (:schema (safe-get component/meta k)))

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
; Also decided not to prefix :schema/ or :d/ because we pass through schema
; some malli schemas like :boolean/:string/:enum/:qualified-keyword (?)
; :map too?

; Maybe use spec, no need to abuse defcomponent as global registry?

;;;;

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :sound [_] :string)

(defmethod schema :image [_]
  [:map {:closed true}
   [:file :string]
   [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]])

(defmethod schema :schema/animation [_]
  [:map {:closed true}
   [:frames :some] ; FIXME actually images
   [:frame-duration pos?]
   [:looping? :boolean]])

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
          (keys component/meta)))

(defmethod schema :components-ns [[_ ns-name-k]]
  (schema [:map-optional (namespaced-ks ns-name-k)]))
