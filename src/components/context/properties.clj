(ns components.context.properties
  (:require [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [safe-get mapvals]]
            [core.component :refer [defcomponent] :as component]
            [core.components :as components]))

(defn- of-type?
  ([property-type {:keys [property/id]}]
   (= (namespace id)
      (:id-namespace property-type)))
  ([types property type]
   (of-type? (type types) property)))

(defn- property->type [types property]
  {:post [%]}
  (some (fn [[type property-type]]
          (when (of-type? property-type property)
            type))
        types))

(defn- validation-error-message [schema property]
  (let [explained (m/explain schema property)]
    (str (me/humanize explained))))

(def ^:private validate? true)

(defn- validate [property types]
  (if validate?
    (try (let [type (property->type types property)
               schema (:schema (type types))]
           (if (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property :type type} t))))
             property
             (throw (ex-info (validation-error-message schema property)
                             {:property property}))))
         (catch Throwable t
           (throw (ex-info "" {:types types :property property} t))))
    property))

(defn- map-attribute-schema [[id-attribute attr-ks]]
  (let [schema-form (apply vector :map {:closed true} id-attribute
                           (component/attribute-schema attr-ks))]
    (try (m/schema schema-form)
         (catch Throwable t
           (throw (ex-info "" {:schema-form schema-form} t))))))

(defcomponent :context/properties
  {:data :some
   :let {:keys [types file properties]}}
  (component/create [_ ctx]
    (doseq [[k m] [[:property/id    {:data [:qualified-keyword {}] :optional? false}]
                   [:entity-effects {:data [:components-ns :effect.entity]}]]]
      (component/defcomponent* k m :warn-on-override? false))
    (let [types (component/ks->create-all types {})
          types (mapvals #(update % :schema map-attribute-schema) types)]
      {:file file
       :types types
       :db (mapvals #(-> %
                         (validate types)
                         (component/apply-system component/edn->value ctx))
                    properties)})))

(defn- pprint-spit [file data]
  (binding [*print-level* nil]
    (->> data
         clojure.pprint/pprint
         with-out-str
         (spit file))))

(defn- sort-by-type [types properties]
  (sort-by #(components/sort-index-of (property->type types %))
           properties))

(def ^:private write-to-file? true)

(defn- sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (sort-map %)
                        %)
                     (vals m)))))

(defn- write-properties-to-file! [{{:keys [types db file]} :context/properties :as ctx}]
  (when write-to-file?
    (.start
     (Thread.
      (fn []
        (->> db
             vals
             (sort-by-type types)
             (map #(component/apply-system % component/value->edn))
             (map sort-map)
             (pprint-spit file)))))))

(extend-type core.context.Context
  core.context/PropertyStore
  (get-property [{{:keys [db]} :context/properties} id]
    (safe-get db id))

  (all-properties [{{:keys [db types]} :context/properties :as ctx} type]
    (filter #(of-type? types % type) (vals db)))

  (overview [{{:keys [types]} :context/properties} property-type]
    (:overview (property-type types)))

  (property-types [{{:keys [types]} :context/properties}]
    (keys types))

  (update! [{{:keys [db types]} :context/properties :as ctx}
            {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id) ; <=  part of validate - but misc does not have property/id -> add !
           (contains? db id)]}
    (validate property types)
    ;(binding [*print-level* nil] (clojure.pprint/pprint property))
    (let [new-ctx (update-in ctx [:context/properties :db] assoc id property)]
      (write-properties-to-file! new-ctx)
      new-ctx))

  (delete! [{{:keys [db]} :context/properties :as ctx}
            property-id]
    {:pre [(contains? db property-id)]}
    (let [new-ctx (update-in ctx [:context/properties :db] dissoc property-id)]
      (write-properties-to-file! new-ctx)
      new-ctx)))

(require '[core.context :as ctx])

(defn- migrate [property-type prop-fn]
  (def validate? false)
  (let [ctx @app/state]
    (def write-to-file? false)
    (time
     (doseq [prop (map prop-fn (ctx/all-properties ctx property-type))]
       (println (:property/id prop) ", " (:property/pretty-name prop))
       (swap! app/state ctx/update! prop)))
    (def write-to-file? true)
    (write-properties-to-file! @app/state)
    (def validate? true)
    nil))


(comment

 (migrate :properties/creature
          (fn [prop]
            (-> prop
                (dissoc :entity/reaction-time)
                (update :property/stats assoc :stats/reaction-time
                        (max (int (/ (:entity/reaction-time prop) 0.016))
                             2)))))
 )
