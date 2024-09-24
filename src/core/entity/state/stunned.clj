(ns ^:no-doc core.entity.state.stunned
  (:require [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.entity.state :as state]
            [core.graphics :as g]
            [core.ctx.time :as time]  ))

(defcomponent :stunned
  {:let {:keys [eid counter]}}
  (component/create [[_ eid duration] ctx]
    {:eid eid
     :counter (time/->counter ctx duration)})

  (state/player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (state/pause-game? [_]
    false)

  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (entity/render-below [_ entity* g _ctx]
    (g/draw-circle g (:position entity*) 0.5 [1 1 1 0.6])))
