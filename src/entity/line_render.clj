(ns entity.line-render
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]
            [api.effect :as effect]))

(defcomponent :entity/line-render {}
  (entity/render-default [[_ {:keys [thick? end color]}] entity* g _ctx]
    (let [position (:position entity*)]
      (if thick?
        (g/with-shape-line-width g 4 #(g/draw-line g position end color))
        (g/draw-line g position end color)))))

(defcomponent :tx.entity/line-render {}
  (effect/do!
   [[_ {:keys [start end duration color thick?]}] _ctx]
   [[:tx/create
     {:position start
      :width 0.5
      :height 0.5
      :z-order :z-order/effect}
     #:entity {:line-render {:thick? thick? :end end :color color}
               :delete-after-duration duration}]]))
