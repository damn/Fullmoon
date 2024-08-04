(ns entity-state.npc-dead
  (:require [api.entity-state :as state]))

(defrecord NpcDead []
  state/State
  (enter [_ {:keys [entity/id entity/position]} _ctx]
    [[:tx/destroy id]
     [:tx/audiovisual position :audiovisuals/creature-die]])
  (exit [_ entity* _ctx])
  (tick [_ entity* _ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
