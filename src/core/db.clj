(ns core.db
  (:refer-clojure :exclude [get])
  (:require [clojure.edn :as edn]
            [clojure.gdx.utils :refer [bind-root safe-get]]
            [clojure.pprint :refer [pprint]]
            [core.component :as component]
            [core.data :as data]
            [core.property :as property]))

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
     [k schema-props (component/data-schema k)])))
; why does a schema depend on compnents ???
; should be independent of components ?!

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/attributes)))

(defmethod data/schema :sound [_]
  :string)

(defmethod data/schema :image [_]
  [:map {:closed true}
   [:file :string]
   [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]])

(defmethod data/schema :data/animation [_]
  [:map {:closed true}
   [:frames :some]
   [:frame-duration pos?]
   [:looping? :boolean]])

(defmethod data/schema :map [[_ ks]]
  (map-schema ks))

(defmethod data/schema :map-optional [[_ ks]]
  (map-schema (map (fn [k] [k {:optional true}]) ks)))

(defmethod data/schema :components-ns [[_ ns-name-k]]
  (data/schema [:map-optional (namespaced-ks ns-name-k)]))

(defmethod data/schema :one-to-many [[_ property-type]]
  [:set [:qualified-keyword {:namespace (property/type->id-namespace property-type)}]])

(defmethod data/schema :one-to-one [[_ property-type]]
  [:qualified-keyword {:namespace (property/type->id-namespace property-type)}])

(declare db
         ^:private edn-file)

(defn load! [file]
  (let [properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! property/validate properties)
    (bind-root #'db (zipmap (map :property/id properties) properties))
    (bind-root #'edn-file file)))

(defn- async-pprint-spit! [properties]
  (.start
   (Thread.
    (fn []
      (binding [*print-level* nil]
        (->> properties
             pprint
             with-out-str
             (spit edn-file)))))))

(defn- recur-sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (recur-sort-map %)
                        %)
                     (vals m)))))

(defn- async-write-to-file! []
  (->> db
       vals
       (sort-by property/->type)
       (map recur-sort-map)
       doall
       async-pprint-spit!))

(defn get [id]
  (property/build (safe-get db id)))

(defn all [type]
  (->> (vals db)
       (filter #(= type (property/->type %)))
       (map property/build)))

(defn update! [{:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? db id)]}
  (property/validate property)
  (alter-var-root #'db assoc id property)
  (async-write-to-file!))

(defn delete! [property-id]
  {:pre [(contains? db property-id)]}
  (alter-var-root #'db dissoc property-id)
  (async-write-to-file!))
