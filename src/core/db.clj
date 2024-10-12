(ns core.db
  (:refer-clojure :exclude [get])
  (:require [clojure.edn :as edn]
            [clojure.gdx.utils :refer [bind-root safe-get]]
            [clojure.pprint :refer [pprint]]
            [core.data :as data]
            [core.property :as property]))

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

(defmethod data/edn->value :one-to-one [_ property-id]
  (get property-id))

(defmethod data/edn->value :one-to-many [_ property-ids]
  (map get property-ids))

(defmethod data/schema :one-to-one [[_ property-type]]
  [:qualified-keyword {:namespace (property/type->id-namespace property-type)}])

(defmethod data/schema :one-to-many [[_ property-type]]
  [:set [:qualified-keyword {:namespace (property/type->id-namespace property-type)}]])
