(ns entity.clickable
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.entity :as entity]))

(defcomponent :entity/clickable {}
  (entity/render-default [[_ {:keys [text]}]
                          {[x y] :entity/position :keys [entity/mouseover? entity/body]}
                          g
                          _ctx]
    (when (and mouseover? text)
      (g/draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height body))
                    :up? true}))))
