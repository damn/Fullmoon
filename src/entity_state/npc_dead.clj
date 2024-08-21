(ns entity-state.npc-dead
  (:require [api.entity :as entity]
            [api.entity-state :as state]))

(defrecord NpcDead []
  state/State
  (enter [_ entity _ctx]
    [[:tx/destroy entity]
     [:tx.entity/audiovisual (:position @entity) :audiovisuals/creature-die]])
  (exit [_ entity _ctx])
  (tick [_ _entity _ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
