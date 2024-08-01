(ns entity.line-render
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/line-render {}
  (entity/render-default [[_ {:keys [thick? end color]}]
                          {:keys [entity/position]}
                          g
                          _ctx]
    (if thick?
      (g/with-shape-line-width g 4 #(g/draw-line g position end color))
      (g/draw-line g position end color))))
