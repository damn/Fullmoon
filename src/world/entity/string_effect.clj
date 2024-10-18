(ns ^:no-doc world.entity.string-effect
  (:require [component.core :refer [defc]]
            [component.tx :as tx]
            [gdx.graphics :as g]
            [world.core :as world :refer [timer stopped?]]
            [world.entity :as entity]))

(defc :entity/string-effect
  (entity/tick [[k {:keys [counter]}] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity]
    (let [[x y] (:position entity)]
      (g/draw-text {:text text
                    :x x
                    :y (+ y (:half-height entity) (g/pixels->world-units 5))
                    :scale 2
                    :up? true}))))

(defc :tx/add-text-effect
  (tx/do! [[_ eid text]]
    [[:e/assoc
      eid
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @eid)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter world/reset))
        {:text text
         :counter (timer 0.4)})]]))
