(ns core.property
  (:require [core.component :refer [defcomponent]]))

(defcomponent :property/id {:data [:qualified-keyword {}]})

(defn- ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))
