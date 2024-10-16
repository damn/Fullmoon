(ns core.property
  (:refer-clojure :exclude [type]))

(defn image [property]
  (or (:entity/image property)
      (first (:frames (:entity/animation property)))))

(defn type->id-namespace [property-type]
  (keyword (name property-type)))

(defn type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))
