(ns world.entity.image)

(defc :entity/image
  {:data :image
   :let image}
  (entity/render [_ entity*]
    (g/draw-rotated-centered-image image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
