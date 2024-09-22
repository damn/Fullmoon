(ns core.entity.string-effect
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]
            [core.g :as g]
            [core.ctx.time :as time]
            [core.tx :as tx]))

(defcomponent :entity/string-effect
  (entity/tick [[k {:keys [counter]}] eid context]
    (when (time/stopped? context counter)
      [[:tx/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units g entity/hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defcomponent :tx/add-text-effect
  (tx/do! [[_ entity text] ctx]
    [[:tx/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(time/reset ctx %)))
        {:text text
         :counter (time/->counter ctx 0.4)})]]))
