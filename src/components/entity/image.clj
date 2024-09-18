(ns components.entity.image
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]
            [core.graphics :as g]))

(defcomponent :entity/image
  {:data :image
   :let image}
  (entity/render [_ entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
