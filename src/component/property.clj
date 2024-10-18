(ns component.property
  (:refer-clojure :exclude [def type])
  (:require [component.core :refer [defc] :as component]
            [component.schema :as schema]
            [malli.core :as m]
            [malli.error :as me]))

(defc :property/id {:schema :qualified-keyword})

(defn def [k {:keys [schema overview]}]
  (defc k
    {:schema [:s/map (conj schema :property/id)]
     :overview overview}))

(defn type->id-namespace [property-type]
  (keyword (name property-type)))

(defn type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn types []
  (filter #(= "properties" (namespace %)) (keys component/meta)))

(defn m-schema [property]
  (-> property type schema/form-of))

(defn- invalid-ex-info [m-schema value]
  (ex-info (str (me/humanize (m/explain m-schema value)))
           {:value value
            :schema (m/form m-schema)}))

(defn validate! [property]
  (let [m-schema (m/schema (m-schema property))]
    (when-not (m/validate m-schema property)
      (throw (invalid-ex-info m-schema property)))))

(defn overview [property-type]
  (:overview (component/meta property-type)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))
