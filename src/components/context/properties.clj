(ns components.context.properties
  (:require [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [safe-get mapvals]]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.property :as property]))

(defcomponent :property/id {:data [:qualified-keyword {}]})

(defn- property->schema [types property]
  (-> property property/->type types :schema))

(defn- validate [property types]
  (let [schema (property->schema types property)
        valid? (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property} t))))]
    (when-not valid?
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

(defcomponent :context/properties
  {:data :some
   :let {:keys [types file properties]}}
  (component/create [_ ctx]
    (let [types (create-types types)]
      (doseq [[_ property] properties]
        (validate property types))
      {:file file
       :types types
       :db properties})))

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

(defn- recur-sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (recur-sort-map %)
                        %)
                     (vals m)))))

(defn- async-write-to-file! [{{:keys [db file]} :context/properties :as ctx}]
  (->> db
       vals
       (sort-by property/->type)
       (map recur-sort-map)
       doall
       (async-pprint-spit! ctx file))
  ctx)

(defn- try-data [k]
  (try (component/k->data k)
       (catch Throwable t)))

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

(defn- build-property [ctx property]
  (apply-kvs (fetch-refs ctx property)
             (fn [k v]
               (data/edn->value (component/data-component k) v ctx))))

(extend-type core.context.Context
  core.context/PropertyStore
  (property [{{:keys [db]} :context/properties :as ctx} id]
    (build-property ctx (safe-get db id)))

  (all-properties [{{:keys [db]} :context/properties :as ctx} type]
    (->> (vals db)
         (filter #(= type (property/->type %)))
         (map #(build-property ctx %))))

  (overview [{{:keys [types]} :context/properties} property-type]
    (:overview (property-type types)))

  (property-types [{{:keys [types]} :context/properties}]
    (keys types))

  (property->schema [{{:keys [types]} :context/properties} property]
    (property->schema types property))

  (update! [{{:keys [db types]} :context/properties :as ctx} {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id)
           (contains? db id)]}
    (validate property types)
    (-> ctx
        (update-in [:context/properties :db] assoc id property)
        async-write-to-file!))

  (delete! [{{:keys [db]} :context/properties :as ctx} property-id]
    {:pre [(contains? db property-id)]}
    (-> ctx
        (update-in [:context/properties :db] dissoc property-id)
        async-write-to-file!)))


(comment
 (defn- migrate [property-type prop-fn]
     (let [ctx @app/state]
       (time
        ; TODO work directly on edn, no ctx/all-properties, use :db
        (doseq [prop (map prop-fn (ctx/all-properties ctx property-type))]
          (println (:property/id prop) ", " (:property/pretty-name prop))
          (swap! app/state ctx/update! prop)))
       (async-write-to-file! @app/state)
       nil))

 (migrate :properties/creature
          (fn [prop]
            (-> prop
                (dissoc :entity/reaction-time)
                (update :property/stats assoc :stats/reaction-time
                        (max (int (/ (:entity/reaction-time prop) 0.016))
                             2)))))
 )
