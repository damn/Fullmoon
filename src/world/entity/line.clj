(ns ^:no-doc world.entity.line
  (:require [clojure.gdx.graphics :as g]
            [core.component :refer [defc]]
            [core.tx :as tx]
            [world.entity :as entity]))

(defc :entity/line-render
  {:let {:keys [thick? end color]}}
  (entity/render [_ entity]
    (let [position (:position entity)]
      (if thick?
        (g/with-shape-line-width 4 #(g/draw-line position end color))
        (g/draw-line position end color)))))

(defc :tx/line-render
  (tx/do! [[_ {:keys [start end duration color thick?]}]]
    [[:e/create
      start
      entity/effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))
