(ns core.entity-state.npc-dead
  (:require [core.component :as component :refer [defcomponent]]
            [core.state :as state]))

(defcomponent :npc-dead
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/enter [_ _ctx]
    [[:tx/destroy eid]]))
