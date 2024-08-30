(ns core.property)

(defn property->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))
