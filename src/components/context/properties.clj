(ns components.context.properties
  (:require [clojure.edn :as edn]
            [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [safe-get mapvals]]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.property :as property]))

(defcomponent :property/id {:data [:qualified-keyword {}]})

(defn load-raw-properties [file]
  (let [values (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id values)))
    (zipmap (map :property/id values) values)))

(defn- property->schema [types property]
  (-> property property/->type types :schema))

(defn- validate [property types]
  (let [schema (property->schema types property)]
    (if (try (m/validate schema property)
             (catch Throwable t
               (throw (ex-info "m/validate fail" {:property property} t))))
      property
      (throw (ex-info (str (me/humanize (m/explain schema property)))
                      {:property property
                       :schema (m/form schema)})))))
(defn- apply-kvs
  "Calls for every key in map (f k v) to calculate new value at k."
  [m f]
  (reduce (fn [m k]
            (assoc m k (f k (get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defn- map-attribute-schema [id-ns-k attr-ks]
  (let [schema-form (apply vector :map {:closed true}
                           [:property/id [:qualified-keyword {:namespace id-ns-k}]]
                           (component/attribute-schema attr-ks))]
    (try (m/schema schema-form)
         (catch Throwable t
           (throw (ex-info "" {:schema-form schema-form} t))))))

(defn create-types [types]
  (apply-kvs (component/ks->create-all types {})
             (fn [k v]
               (update v :schema #(map-attribute-schema (keyword (name k)) %)))))

(defn- try-data [k]
  (try (component/k->data k)
       (catch Throwable t)))

(defn- edn->value [ctx]
  (fn [k v]
    (if-let [f (:edn->value (component/k->data k))]
      (f v ctx)
      v)))

(defn- value->edn [k v]
  (if-let [f (:value->edn (try-data k))]
    (f v)
    v))

(defn- recur-value->edn [property]
  (apply-kvs property
             (fn [k v]
               (let [v (if (map? v)
                         (recur-value->edn v)
                         v)]
                 (value->edn k v)))))

(defn- edn->db [properties types ctx]
  (mapvals #(-> %
                (validate types)
                (apply-kvs (edn->value ctx)))
           properties))

(defn- recur-sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (recur-sort-map %)
                        %)
                     (vals m)))))

(defn- db->edn [types db]
  (->> db
       vals
       (sort-by property/->type)
       (map recur-value->edn)
       (map #(validate % types))
       (map recur-sort-map)))

(defcomponent :context/properties
  {:data :some
   :let {:keys [types file properties]}}
  (component/create [_ ctx]
    (let [types (create-types types)]
      {:file file
       :types types
       :db (edn->db properties types ctx)})))

(defn- async-pprint-spit! [ctx file data]
  (.start
   (Thread.
    (fn []
      (try (binding [*print-level* nil]
             (->> data
                  clojure.pprint/pprint
                  with-out-str
                  (spit file)))
           (catch Throwable t
             (ctx/error-window! ctx t)))))))

(defn- validate-and-write-to-file! [{{:keys [types db file]} :context/properties :as ctx}]
  (->> db
       (db->edn types)
       doall
       (async-pprint-spit! ctx file))
  ctx)

; Fetching references at ctx/property and not immediately on db creation
; so changes in one property will not get lost at referencing properties
; also it broke nested fetchs like :entity/skills -> :skills/projectile -> skill/effects
; (would need one more level of recur for collections/seqs etc.)
(defn- fetch-refs [ctx property]
  (apply-kvs property
             (fn [k v]
               (let [v (if (map? v)
                         (fetch-refs ctx v)
                         v)]
                 (if-let [f (:fetch-references (try-data k))]
                   (f ctx v)
                   v)))))

(extend-type core.context.Context
  core.context/PropertyStore
  (property [{{:keys [db]} :context/properties :as ctx} id]
    (fetch-refs ctx (safe-get db id)))

  (all-properties [{{:keys [db]} :context/properties :as ctx} type]
    (->> (vals db)
         (filter #(= type (property/->type %)))
         (map #(fetch-refs ctx %))))

  (overview [{{:keys [types]} :context/properties} property-type]
    (:overview (property-type types)))

  (property-types [{{:keys [types]} :context/properties}]
    (keys types))

  (property->schema [{{:keys [types]} :context/properties} property]
    (property->schema types property))

  (update! [{{:keys [db]} :context/properties :as ctx} {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id)
           (contains? db id)]}
    (-> ctx
        (update-in [:context/properties :db] assoc id property)
        validate-and-write-to-file!))

  (delete! [{{:keys [db]} :context/properties :as ctx} property-id]
    {:pre [(contains? db property-id)]}
    (-> ctx
        (update-in [:context/properties :db] dissoc property-id)
        validate-and-write-to-file!)))


(comment

 ; now broken -> work directly on edn
 #_(require '[core.context :as ctx])

 #_(defn- migrate [property-type prop-fn]
     (let [ctx @app/state]
       (time
        (doseq [prop (map prop-fn (ctx/all-properties ctx property-type))]
          (println (:property/id prop) ", " (:property/pretty-name prop))
          (swap! app/state ctx/update! prop)))
       (validate-and-write-to-file! @app/state)
       nil))

 (migrate :properties/creature
          (fn [prop]
            (-> prop
                (dissoc :entity/reaction-time)
                (update :property/stats assoc :stats/reaction-time
                        (max (int (/ (:entity/reaction-time prop) 0.016))
                             2)))))
 )
