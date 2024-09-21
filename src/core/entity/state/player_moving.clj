(ns core.entity.state.player-moving
  (:require [utils.wasd-movement :refer [WASD-movement-vector]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.state :as state]))

(defcomponent :player-moving
  {:let {:keys [eid movement-vector]}}
  (component/create [[_ eid movement-vector] _ctx]
    {:eid eid
     :movement-vector movement-vector})

  (state/player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (state/pause-game? [_]
    false)

  (state/enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity/stat @eid :stats/movement-speed)}]])

  (state/exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid context]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity/stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))
