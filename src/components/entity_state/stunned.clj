(ns components.entity-state.stunned
  (:require [core.component :as component :refer [defcomponent]]
            [core.graphics :as g]
            [core.context :refer [stopped? ->counter]]
            [core.entity :as entity]
            [core.entity-state :as state]))

(defcomponent :stunned
  {:let {:keys [eid counter]}}
  (component/create [[_ eid duration] ctx]
    {:eid eid
     :counter (->counter ctx duration)})

  (state/player-enter [_]
    [[:tx.context.cursor/set :cursors/denied]])

  (state/pause-game? [_]
    false)

  (entity/tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (entity/render-below [_ entity* g _ctx]
    (g/draw-circle g (:position entity*) 0.5 [1 1 1 0.6])))
