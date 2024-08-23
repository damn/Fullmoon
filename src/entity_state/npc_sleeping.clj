(ns entity-state.npc-sleeping
  (:require [core.graphics :as g]
            [gdx.graphics.color :as color]
            [core.context :refer [world-grid]]
            [core.entity :as entity]
            [core.entity-state :as state]
            [core.world.cell :as cell]))

; TODO pass to creature data, also @ shout
(def ^:private aggro-range 6)

(defrecord NpcSleeping [eid]
  state/State
  (enter [_ _ctx])

  (exit [_ ctx]
    ; TODO make state = alerted, and shout at the end of that !
    ; then nice alert '!' and different entities different alert time
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx.entity/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (tick [_ context]
    (let [entity* @eid
          cell ((world-grid context) (entity/tile entity*))]
      (when-let [distance (cell/nearest-entity-distance @cell (entity/enemy-faction entity*))]
        (when (<= distance aggro-range)
          [[:tx/event eid :alert]]))))

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true})))
  (render-info [_ entity* g ctx]))

(defn ->build [ctx eid _params]
  (->NpcSleeping eid))
