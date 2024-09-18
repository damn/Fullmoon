(ns components.entity-state.player-moving
  (:require [utils.wasd-movement :refer [WASD-movement-vector]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]))

(defcomponent :player-moving
  {:let {:keys [eid movement-vector]}}
  (component/create [[_ eid movement-vector] _ctx]
    {:eid eid
     :movement-vector movement-vector})

  (component/player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (component/pause-game? [_]
    false)

  (component/enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity/stat @eid :stats/movement-speed)}]])

  (component/exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid context]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity/stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))
