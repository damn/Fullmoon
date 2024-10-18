(ns moon.creature.stunned
  (:require [component.core :refer [defc]]
            [gdx.graphics :as g]
            [world.core :refer [timer stopped?]]
            [world.entity :as entity]
            [world.entity.state :as state]))

(defc :stunned
  {:let {:keys [eid counter]}}
  (entity/->v [[_ eid duration]]
    {:eid eid
     :counter (timer duration)})

  (state/player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (state/pause-game? [_]
    false)

  (entity/tick [_ eid]
    (when (stopped? counter)
      [[:tx/event eid :effect-wears-off]]))

  (entity/render-below [_ entity]
    (g/draw-circle (:position entity) 0.5 [1 1 1 0.6])))
