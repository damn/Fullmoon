(ns components.entity-state.npc-dead
  (:require [core.component :as component :refer [defcomponent]]))

(defcomponent :npc-dead
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (component/enter [_ _ctx]
    [[:tx/destroy eid]]))
