(ns components.entity.image
  (:require [core.component :refer [defcomponent]]
            [core.graphics :as g]
            [core.entity :as entity]))

(defcomponent :entity/image
  {:let image}
  (entity/render-default [_ entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
