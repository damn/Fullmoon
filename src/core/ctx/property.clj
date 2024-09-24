(ns core.ctx.property
  (:require [clojure.edn :as edn]
            clojure.pprint
            [malli.core :as m]
            [malli.error :as me]
            [core.utils.core :refer [safe-get]]
            [core.ctx :refer :all]))

(defsystem ->value "..." [_])

(defn data-component [k]
  (try (let [data (:data (safe-get component-attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component-attributes data)]))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))

(defmulti edn->value (fn [data v ctx] (if data (data 0))))
(defmethod edn->value :default [_data v _ctx]
  v)

(defn- k->widget [k]
  (cond
   (#{:map-optional :components-ns} k) :map
   (#{:number :nat-int :int :pos :pos-int :val-max} k) :number
   :else k))

(defmulti ->widget      (fn [[k _] _v _ctx] (k->widget k)))
(defmulti widget->value (fn [[k _] _widget] (k->widget k)))

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))

(defn property-type->id-namespace [property-type]
  (keyword (name property-type)))

(defn ns-k->property-type [ns-k]
  (keyword "properties" (name ns-k)))

(defn- ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))

(defn types []
  (filter #(= "properties" (namespace %)) (keys component-attributes)))

(defn overview [property-type]
  (:overview (get component-attributes property-type)))

(defn ->schema [property]
  (-> property
      ->type
      data-component
      (get 1)
      :schema
      m/schema))

(defcomponent :property/id {:data [:qualified-keyword]})

(defn def-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(defn- validate [property]
  (let [schema (->schema property)
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
      (binding [*print-level* nil]
        (->> data
             clojure.pprint/pprint
             with-out-str
             (spit file)))))))

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
       (sort-by ->type)
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
               (edn->value (try (data-component k)
                                (catch Throwable _t
                                  (swap! undefined-data-ks conj k)))
                           (if (map? v) (build-property ctx v) v)
                           ctx))))

(defn build [{{:keys [db]} :context/properties :as ctx} id]
  (build-property ctx (safe-get db id)))

(defn all-properties [{{:keys [db]} :context/properties :as ctx} type]
  (->> (vals db)
       (filter #(= type (->type %)))
       (map #(build-property ctx %))))

(defn update! [{{:keys [db]} :context/properties :as ctx} {:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? db id)]}
  (validate property)
  (-> ctx
      (update-in [:context/properties :db] assoc id property)
      async-write-to-file!))

(defn delete! [{{:keys [db]} :context/properties :as ctx} property-id]
  {:pre [(contains? db property-id)]}
  (-> ctx
      (update-in [:context/properties :db] dissoc property-id)
      async-write-to-file!))

(comment
 (defn- migrate [property-type prop-fn]
   (let [ctx @app/state]
     (time
      ; TODO work directly on edn, no all-properties, use :db
      (doseq [prop (map prop-fn (all-properties ctx property-type))]
        (println (:property/id prop) ", " (:property/pretty-name prop))
        (swap! app/state update! prop)))
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
