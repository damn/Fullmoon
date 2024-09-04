(ns core.property)

(defn ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))
