(ns components.entity.string-effect
  (:require [core.component :refer [defcomponent]]
            [core.graphics :as g]
            [core.context :refer [->counter stopped? reset]]
            [core.entity :as entity]
            [core.effect :as effect]))

(defcomponent :entity/string-effect {}
  (entity/tick [[k {:keys [counter]}] eid context]
    (when (stopped? context counter)
      [[:tx.entity/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units g entity/hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defcomponent :tx/add-text-effect {}
  (effect/do! [[_ entity text] ctx]
    [[:tx.entity/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(reset ctx %)))
        {:text text
         :counter (->counter ctx 0.4)})]]))
