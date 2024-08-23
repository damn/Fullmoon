(ns entity-state.npc-dead
  (:require [core.entity :as entity]
            [core.entity-state :as state]))

(defrecord NpcDead [eid]
  state/State
  (enter [_ _ctx]
    [[:tx/destroy eid]])

  (exit [_ _ctx])
  (tick [_ _ctx])

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->build [ctx eid _params]
  (->NpcDead eid))
