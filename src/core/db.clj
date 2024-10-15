(ns core.db
  (:refer-clojure :exclude [get])
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [core.data :as data]
            [core.property :as property]
            [utils.core :refer [bind-root safe-get]]))

(declare db
         ^:private edn-file)

(defn load! [file]
  (let [file (clojure.java.io/resource file) ; load here and not in threading macro so #'edn-file correct (tests?!)
        properties (-> file slurp edn/read-string)]
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

(defmulti edn->value (fn [data v]
                       (when data  ; undefined-data-ks
                         (data/type data))))
(defmethod edn->value :default [_data v] v)

(defn- build [property]
  (apply-kvs property
             (fn [k v]
               (try (edn->value (try (data/component k)
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
  (property/validate property)
  (alter-var-root #'db assoc id property)
  (async-write-to-file!))

(defn delete! [property-id]
  {:pre [(contains? db property-id)]}
  (alter-var-root #'db dissoc property-id)
  (async-write-to-file!))

(defmethod edn->value :one-to-one [_ property-id]
  (get property-id))

(defmethod edn->value :one-to-many [_ property-ids]
  (map get property-ids))

(defmethod data/schema :one-to-one [[_ property-type]]
  [:qualified-keyword {:namespace (property/type->id-namespace property-type)}])

(defmethod data/schema :one-to-many [[_ property-type]]
  [:set [:qualified-keyword {:namespace (property/type->id-namespace property-type)}]])
