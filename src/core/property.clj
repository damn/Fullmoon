(ns core.property
  (:refer-clojure :exclude [def])
  (:require [clojure.gdx.utils :refer [safe-get]]
            [core.component :refer [defc] :as component]
            [core.data :as data]
            [malli.core :as m]
            [malli.error :as me]))

(defc :property/id {:data :qualified-keyword})

(defn def [k {:keys [schema overview]}]
  (defc k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(defn type->id-namespace [property-type]
  (keyword (name property-type)))

(defn ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ns-k->type [ns-k]
  (keyword "properties" (name ns-k)))

(defn ->schema [property]
  (-> property
      ->type
      component/data
      data/schema
      m/schema))

(defn validate [property]
  (let [schema (->schema property)
        valid? (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property} t))))]
    (when-not valid?
      (throw (ex-info (str (me/humanize (m/explain schema property)))
                      {:property property
                       :schema (m/form schema)})))))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))

(defn types []
  (filter #(= "properties" (namespace %)) (keys component/attributes)))

(defn overview [property-type]
  (:overview (get component/attributes property-type)))

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
            (assoc m k (f k (get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defmulti edn->value (fn [data v] (data/type data)))
(defmethod edn->value :default [_data v] v)

(defn build [property]
  (apply-kvs property
             (fn [k v]
               (try (edn->value (try (component/data k)
                                     (catch Throwable _t
                                       (swap! undefined-data-ks conj k)
                                       nil))
                                (if (map? v)
                                  (build v)
                                  v))
                    (catch Throwable t
                      (throw (ex-info " " {:k k :v v})))))))
