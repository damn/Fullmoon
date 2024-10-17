(ns ^:no-doc world.entity.image
  (:require [gdx.graphics :as g]
            [core.component :refer [defc]]
            [world.entity :as entity]))

(defc :entity/image
  {:data :image
   :let image}
  (entity/render [_ entity]
    (g/draw-rotated-centered-image image
                                   (or (:rotation-angle entity) 0)
                                   (:position entity))))
