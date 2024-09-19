(ns core.property
  (:require [malli.core :as m]
            [core.component :as component :refer [defcomponent]]
            [core.data :as data]))

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
      data/component
      (get 1)
      :schema
      m/schema))

(defcomponent :property/id {:data [:qualified-keyword]})

(defn def-property-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))
