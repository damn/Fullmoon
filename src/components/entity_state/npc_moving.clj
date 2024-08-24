(ns components.entity-state.npc-moving
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :refer [stopped? ->counter]]
            [core.entity :as entity]
            [core.entity-state :as state]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfinding/usable skills every frame
; also prevents fast twitching around changing directions every frame
(defcomponent :npc-moving
  {:let {:keys [eid movement-vector counter]}}
  (component/create [[_ eid movement-vector] ctx]
    {:eid eid
     :movement-vector movement-vector
     :counter (->counter ctx (:entity/reaction-time @eid))})

  (state/enter [_ _ctx]
    [[:tx.entity/set-movement eid {:direction movement-vector
                                   :speed (or (entity/stat @eid :stats/movement-speed) 0)}]])

  (state/exit [_ _ctx]
    [[:tx.entity/set-movement eid nil]])

  (entity/tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :timer-finished]])))
