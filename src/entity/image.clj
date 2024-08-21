(ns entity.image
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/image {}
  (entity/render-default [[_ image] entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (entity/position entity*))))
