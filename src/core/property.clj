(ns core.property)

(defn property->image [{:keys [entity/image entity/animation]}]
  {:post [%]}
  (or image
      (first (:frames animation))))
