(ns core.property
  (:require [malli.core :as m]
            [core.component :as component :refer [defcomponent]]))

(defn property-type->id-namespace [property-type]
  (keyword (name property-type)))

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
      component/data-component
      (get 1)
      :schema
      m/schema))

; doesn't work because getting attribute-schema from defined :property/id ...
; cannot have different schemas for same property/id
#_(defn- type->property-id-schema [k]
    [:property/id [:qualified-keyword {:namespace (property-type->id-namespace k)}]])

(defn def-property-type [k {:keys [attributes overview]}]
  (defcomponent k
    {:data [:map (conj attributes :property/id)]
     :overview overview}))
