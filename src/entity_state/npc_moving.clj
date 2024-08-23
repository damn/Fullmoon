(ns entity-state.npc-moving
  (:require [core.context :refer [stopped? ->counter]]
            [core.entity :as entity]
            [core.entity-state :as state]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfinding/usable skills every frame
; also prevents fast twitching around changing directions every frame
(defrecord NpcMoving [eid movement-vector counter]
  state/State
  (enter [_ _ctx]
    [[:tx.entity/set-movement eid {:direction movement-vector
                                   :speed (or (entity/stat @eid :stats/movement-speed) 0)}]])

  (exit [_ _ctx]
    [[:tx.entity/set-movement eid nil]])

  (tick [_ ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :timer-finished]]))

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->build [ctx eid movement-direction]
  (->NpcMoving eid movement-direction (->counter ctx (:entity/reaction-time @eid))))
