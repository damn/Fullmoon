(ns ^:no-doc core.entity.string-effect
  (:require [core.component :as component]
            [core.ctx :refer :all]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.ctx.time :as time]))

(defcomponent :entity/string-effect
  (entity/tick [[k {:keys [counter]}] eid context]
    (when (time/stopped? context counter)
      [[:e/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units g entity/hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defcomponent :tx/add-text-effect
  (component/do! [[_ entity text] ctx]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(time/reset ctx %)))
        {:text text
         :counter (time/->counter ctx 0.4)})]]))
