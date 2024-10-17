(ns moon.creature.player.moving
  (:require [component.core :refer [defc]]
            [gdx.input :refer [key-pressed?]]
            [gdx.math.vector :as v]
            [world.entity :as entity]
            [world.entity.state :as state]
            [world.entity.stats :refer [entity-stat]]))

(defn- add-vs [vs]
  (v/normalise (reduce v/add [0 0] vs)))

(defn WASD-movement-vector []
  (let [r (when (key-pressed? :d) [1  0])
        l (when (key-pressed? :a) [-1 0])
        u (when (key-pressed? :w) [0  1])
        d (when (key-pressed? :s) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v/length v))
          v)))))

(defc :player-moving
  {:let {:keys [eid movement-vector]}}
  (entity/->v [[_ eid movement-vector]]
    {:eid eid
     :movement-vector movement-vector})

  (state/player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (state/pause-game? [_]
    false)

  (state/enter [_]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity-stat @eid :stats/movement-speed)}]])

  (state/exit [_]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity-stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))
