(ns entity.image
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/image {}
  (entity/render-default [[_ image] {:keys [entity/body] :as entity*} g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (if body (:rotation-angle body) 0)
                                   (entity/position entity*))))
