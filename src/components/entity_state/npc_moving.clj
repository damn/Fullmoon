(ns components.entity-state.npc-moving
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :refer [stopped? ->counter]]
            [core.entity :as entity]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfinding/usable skills every frame
; also prevents fast twitching around changing directions every frame
(defcomponent :npc-moving
  {:let {:keys [eid movement-vector counter]}}
  (component/create [[_ eid movement-vector] ctx]
    {:eid eid
     :movement-vector movement-vector
     :counter (->counter ctx (* (entity/stat @eid :stats/reaction-time) 0.016))})

  (component/enter [_ _ctx]
    [[:tx.entity/set-movement eid {:direction movement-vector
                                   :speed (or (entity/stat @eid :stats/movement-speed) 0)}]])

  (component/exit [_ _ctx]
    [[:tx.entity/set-movement eid nil]])

  (component/tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :timer-finished]])))
