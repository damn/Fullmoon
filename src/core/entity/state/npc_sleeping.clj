(ns ^:no-doc core.entity.state.npc-sleeping
  (:require [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.entity.state :as state]
            [core.ctx.grid :as grid]))

(defcomponent :npc-sleeping
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/exit [_ ctx]
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (entity/tick [_ eid context]
    (let [entity* @eid
          cell ((:context/grid context) (entity/tile entity*))]
      (when-let [distance (grid/nearest-entity-distance @cell (entity/enemy-faction entity*))]
        (when (<= distance (entity/stat entity* :stats/aggro-range))
          [[:tx/event eid :alert]]))))

  (entity/render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true}))))
