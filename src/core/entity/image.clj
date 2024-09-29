(in-ns 'core.entity)

(defcomponent :entity/image
  {:data :image
   :let image}
  (render [_ entity* g _ctx]
    (draw-rotated-centered-image g
                                 image
                                 (or (:rotation-angle entity*) 0)
                                 (:position entity*))))
