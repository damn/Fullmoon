(ns entity.string-effect
  (:require [core.component :refer [defcomponent]]
            [api.graphics :as g]
            [api.context :refer [->counter stopped? reset]]
            [context.ui.config :refer [hpbar-height-px]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

(defcomponent :entity/string-effect {}
  (entity/tick [[k {:keys [counter]}] {:keys [entity/id]} context]
    (when (stopped? context counter)
      [[:tx.entity/dissoc id k]]))

  (entity/render-above [[_ {:keys [text]}] {[x y] :entity/position :keys [entity/body]} g _ctx]
    (g/draw-text g
                 {:text text
                  :x x
                  :y (+ y (:half-height body) (g/pixels->world-units g hpbar-height-px))
                  :scale 2
                  :up? true})))

(defmethod transact! :tx/add-text-effect [[_ entity text] ctx]
  [[:tx.entity/assoc
    entity
    :entity/string-effect
    (if-let [string-effect (:entity/string-effect @entity)]
      (-> string-effect
          (update :text str "\n" text)
          (update :counter #(reset ctx %)))
      {:text text
       :counter (->counter ctx 0.4)})]])
