(ns components.context.properties
  (:require [clojure.edn :as edn]
            clojure.pprint
            [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [safe-get]]
            [core.component :as component  :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            [core.property :as property]))

(defcomponent :property/id {:data [:qualified-keyword {}]})

(defn- validate [property]
  (let [schema (property/schema property)
        valid? (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property} t))))]
    (when-not valid?
      (throw (ex-info (str (me/humanize (m/explain schema property)))
                      {:property property
                       :schema (m/form schema)})))))

(defn validate-and-create [file]
  (let [properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! validate properties)
    {:file file
     :db (zipmap (map :property/id properties) properties)}))

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

(def ^:private undefined-data-ks (atom #{}))

(comment
 #{:frames
   :looping?
   :frame-duration
   :file
   :sub-image-bounds})

(defn- apply-kvs
  "Calls for every key in map (f k v) to calculate new value at k."
  [m f]
  (reduce (fn [m k]
            (assoc m k (f k (get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defn- build-property [ctx property]
  (apply-kvs property
             (fn [k v]
               (data/edn->value (try (component/data-component k)
                                     (catch Throwable _t
                                       (swap! undefined-data-ks conj k)))
                                (if (map? v) (build-property ctx v) v)
                                ctx))))

(extend-type core.context.Context
  core.context/PropertyStore
  (property [{{:keys [db]} :context/properties :as ctx} id]
    (build-property ctx (safe-get db id)))

  (all-properties [{{:keys [db]} :context/properties :as ctx} type]
    (->> (vals db)
         (filter #(= type (property/->type %)))
         (map #(build-property ctx %))))

  (update! [{{:keys [db]} :context/properties :as ctx} {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id)
           (contains? db id)]}
    (validate property)
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
