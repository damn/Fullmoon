(ns core.data
  (:require [clojure.gdx.utils :refer [safe-get]]
            [core.component :as component]))

; probably a better name for this
; :d/type ?
; or document where its used as param or name

; actually I also do this for components
; either just kw or [kw & values]
; so are data types also components?
; with :d/
; => systems need to dispatch like that
; that makes it also clearer what a 'component' is !
; - anything -
; even a namespace --> modules come back
; or a var !
(defn ->type [data]
  (if (vector? data)
    (data 0)
    data))

(defmulti edn->value (fn [data v] (->type data)))
(defmethod edn->value :default [_data v] v)

; if this is just static no need defmethod ?
; (data/def :d/number number?)
(defmulti schema ->type)
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

(defn data [k]
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
     [k schema-props (schema (data k))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defmethod schema :map [[_ ks]]
  (map-schema ks))

(defmethod schema :map-optional [[_ ks]]
  (map-schema (map (fn [k] [k {:optional true}]) ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys attributes)))

(defmethod schema :components-ns [[_ ns-name-k]]
  (schema [:map-optional (namespaced-ks ns-name-k)]))
