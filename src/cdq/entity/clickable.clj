(ns cdq.entity.clickable
  (:require [core.component :as component]
            [gdl.graphics :as g]
            [cdq.api.entity :as entity]))

(component/def :entity/clickable {}
  {:keys [text]}
  (entity/render-default [_ {[x y] :entity/position :keys [entity/mouseover? entity/body]} g _ctx]
    (when (and mouseover? text)
      (g/draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height body))
                    :up? true}))))
