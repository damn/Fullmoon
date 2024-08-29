(ns components.entity.image
  (:require [core.component :as component :refer [defcomponent]]
            [core.graphics :as g]
            [core.image :as image]))

(defcomponent :entity/image
  {:data :image
   :optional? false
   :let image}
  (component/edn->value [_ ctx] (image/edn->image image ctx))
  (component/value->edn [_]     (image/image->edn image))
  (component/render-default [_ entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
