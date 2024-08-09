(ns entity.clickable
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/clickable {}
  (entity/render-default [[_ {:keys [text]}]
                          {:keys [entity/mouseover? entity/body] :as entity*}
                          g
                          _ctx]
    (when (and mouseover? text)
      (let [[x y] (entity/position entity*)]
        (g/draw-text g
                     {:text text
                      :x x
                      :y (+ y (:half-height body))
                      :up? true})))))
