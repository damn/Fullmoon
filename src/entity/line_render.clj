(ns entity.line-render
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

(defcomponent :entity/line-render {}
  (entity/render-default [[_ {:keys [thick? end color]}] entity* g _ctx]
    (let [position (entity/position entity*)]
      (if thick?
        (g/with-shape-line-width g 4 #(g/draw-line g position end color))
        (g/draw-line g position end color)))))

(defmethod transact! :tx.entity/line-render
  [[_ {:keys [start end duration color thick?]}] _ctx]
  [[:tx/create #:entity {:body {:position start
                                :width 0.5
                                :height 0.5
                                :z-order :z-order/effect}
                         :line-render {:thick? thick? :end end :color color}
                         :delete-after-duration duration}]])
