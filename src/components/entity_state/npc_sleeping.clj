(ns components.entity-state.npc-sleeping
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.world.cell :as cell]))

(defcomponent :npc-sleeping
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (component/exit [_ ctx]
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (component/tick [_ eid context]
    (let [entity* @eid
          cell ((ctx/world-grid context) (entity/tile entity*))]
      (when-let [distance (cell/nearest-entity-distance @cell (entity/enemy-faction entity*))]
        (when (<= distance (entity/stat entity* :stats/aggro-range))
          [[:tx/event eid :alert]]))))

  (component/render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true}))))
