(ns entity.line-render
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/line-render {}
  (entity/render-default [[_ {:keys [thick? end color]}] entity* g _ctx]
    (let [position (entity/position entity*)]
      (if thick?
        (g/with-shape-line-width g 4 #(g/draw-line g position end color))
        (g/draw-line g position end color)))))
