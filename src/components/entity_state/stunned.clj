(ns components.entity-state.stunned
  (:require [core.component :as component :refer [defcomponent]]
            [core.graphics :as g]
            [core.context :refer [stopped? ->counter]]))

(defcomponent :stunned
  {:let {:keys [eid counter]}}
  (component/create [[_ eid duration] ctx]
    {:eid eid
     :counter (->counter ctx duration)})

  (component/player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (component/pause-game? [_]
    false)

  (component/tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (component/render-below [_ entity* g _ctx]
    (g/draw-circle g (:position entity*) 0.5 [1 1 1 0.6])))
