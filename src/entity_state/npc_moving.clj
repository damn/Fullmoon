(ns entity-state.npc-moving
  (:require [api.context :refer [stopped? ->counter]]
            [api.entity :as entity]
            [api.entity-state :as state]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfinding/usable skills every frame
; also prevents fast twitching around changing directions every frame
(defrecord NpcMoving [movement-vector counter]
  state/State
  (enter [_ entity _ctx]
    [[:tx.entity/set-movement entity {:direction movement-vector
                                      :speed (or (entity/stat @entity :stats/movement-speed) 0)}]])

  (exit [_ eid _ctx]
    [[:tx.entity/set-movement eid nil]])

  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :timer-finished]]))

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->npc-moving [ctx {:keys [entity/reaction-time]} movement-direction]
  (->NpcMoving movement-direction (->counter ctx reaction-time)))
