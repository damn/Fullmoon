(ns core.property)

(defn property-type->id-namespace [property-type]
  (keyword (name property-type)))

(defn ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))
