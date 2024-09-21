(ns core.property
  (:require [utils.core :refer [safe-get]]
            [malli.core :as m]
            [core.component :as component :refer [defcomponent defsystem defcomponent*]]))

(defsystem ->value "..." [_])

(defn data-component [k]
  (try (let [data (:data (safe-get component/attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component/attributes data)]))
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

(defn ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))

(defn types []
  (filter #(= "properties" (namespace %)) (keys component/attributes)))

(defn overview [property-type]
  (:overview (get component/attributes property-type)))

(defn schema [property]
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
