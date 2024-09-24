(ns ^:no-doc core.entity.line-render
  (:require [core.ctx :refer :all]
            [core.entity :as entity]))

(defcomponent :entity/line-render
  {:let {:keys [thick? end color]}}
  (entity/render [_ entity* g _ctx]
    (let [position (:position entity*)]
      (if thick?
        (with-shape-line-width g 4 #(draw-line g position end color))
        (draw-line g position end color)))))

(defcomponent :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}] _ctx]
    [[:e/create
      start
      entity/effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))
