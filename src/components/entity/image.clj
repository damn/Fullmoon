(ns components.entity.image
  (:require [core.component :as component :refer [defcomponent]]
            [core.graphics :as g]))

(defcomponent :entity/image
  {:data :image
   :optional? false
   :let image}
  (component/render-default [_ entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
