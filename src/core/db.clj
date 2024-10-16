(ns core.db
  (:refer-clojure :exclude [get])
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [core.component :refer [defc] :as component]
            [core.property :as property]
            [core.schema :as s]
            [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [bind-root safe-get]]))

(defn def-attr [k schema]
  (defc k {:db/schema schema}))

(defn attr-schema [k]
  (:db/schema (component/meta k)))

(def-attr :property/id :qualified-keyword)

; overview is only for editor - remove
; remove this whole thing then
(defn def-property [k {:keys [schema overview]}]
  (defc k
    {:db/schema [:map (conj schema :property/id)]
     :overview overview}))

(defn prop-types []
  (filter #(= "properties" (namespace %)) (keys component/meta)))

(defn prop->schema [property]
  (-> property
      property/type
      attr-schema
      s/form
      m/schema))

(defn- invalid-ex-info [schema value]
  (ex-info (str (me/humanize (m/explain schema value)))
           {:value value
            :schema (m/form schema)}))

(defn- validate! [property]
  (let [schema (try (prop->schema property)
                    (catch Throwable t
                      (throw (ex-info "prop->schema" {:property property} t))))]
    (when-not (m/validate schema property)
      (throw (invalid-ex-info schema property)))))

(defn prop-type-overview [property-type]
  (:overview (component/meta property-type)))

(declare db
         ^:private edn-file)

(defn load! [file]
  (let [file (clojure.java.io/resource file) ; load here and not in threading macro so #'edn-file correct (tests?!)
        properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! validate! properties)
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
       (sort-by property/type)
       (map recur-sort-map)
       doall
       async-pprint-spit!))

(def ^:private undefined-data-ks (atom #{}))

(comment
 #{:frames
   :looping?
   :frame-duration
   :file
   :sub-image-bounds})

; reduce-kv?
(defn- apply-kvs
  "Calls for every key in map (f k v) to calculate new value at k."
  [m f]
  (reduce (fn [m k]
            (assoc m k (f k (clojure.core/get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defmulti edn->value (fn [schema v]
                       (when schema  ; undefined-data-ks
                         (s/type schema))))
(defmethod edn->value :default [_ v] v)

(defn- build [property]
  (apply-kvs property
             (fn [k v]
               (try (edn->value (try (attr-schema k)
                                     (catch Throwable _t
                                       (swap! undefined-data-ks conj k)
                                       nil))
                                (if (map? v)
                                  (build v)
                                  v))
                    (catch Throwable t
                      (throw (ex-info " " {:k k :v v} t)))))))

(defn get [id]
  (build (safe-get db id)))

(defn all-raw [type]
  (->> (vals db)
       (filter #(= type (property/type %)))))

(defn all [type]
  (map build (all-raw type)))

(defn update! [{:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? db id)]}
  (validate! property)
  (alter-var-root #'db assoc id property)
  (async-write-to-file!))

(defn delete! [property-id]
  {:pre [(contains? db property-id)]}
  (alter-var-root #'db dissoc property-id)
  (async-write-to-file!))

;;

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
     [k schema-props (s/form (attr-schema k))])))

(defn- map-form [ks]
  (apply vector :map {:closed true} (attribute-form ks)))

(defmethod s/form :map [[_ ks]]
  (map-form ks))

(defmethod s/form :map-optional [[_ ks]]
  (map-form (map (fn [k] [k {:optional true}]) ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/meta)))

(defmethod s/form :components-ns [[_ ns-name-k]]
  (s/form [:map-optional (namespaced-ks ns-name-k)]))

;;

(defmethod s/form :one-to-one [[_ property-type]]
  [:qualified-keyword {:namespace (property/type->id-namespace property-type)}])

(defmethod edn->value :one-to-one [_ property-id]
  (get property-id))

(defmethod s/form :one-to-many [[_ property-type]]
  [:set [:qualified-keyword {:namespace (property/type->id-namespace property-type)}]])

(defmethod edn->value :one-to-many [_ property-ids]
  (map get property-ids))
