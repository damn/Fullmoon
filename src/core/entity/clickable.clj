(ns ^:no-doc core.entity.clickable
  (:require [core.entity :as entity]
            [core.ctx :refer :all]
            [core.graphics :as g]))

(defcomponent :entity/clickable
  (entity/render [[_ {:keys [text]}]
                  {:keys [entity/mouseover?] :as entity*}
                  g
                  _ctx]
    (when (and mouseover? text)
      (let [[x y] (:position entity*)]
        (g/draw-text g
                     {:text text
                      :x x
                      :y (+ y (:half-height entity*))
                      :up? true})))))
