(ns world.game-loop
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.input :refer [key-pressed? key-just-pressed?]]
            [core.tx :as tx]
            [core.widgets.error :refer [error-window!]]
            [utils.core :refer [bind-root]]
            [world.content-grid :as content-grid]
            [world.core :refer [entity-tick-error]]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [world.mouseover-entity :as mouseover-entity]
            [world.player :refer [world-player]]
            [world.potential-fields :as potential-fields]))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-state-pause-game? [] (entity-state/pause-game? (entity-state/state-obj @world-player)))
(defn- player-update-state      [] (entity-state/manual-tick (entity-state/state-obj @world-player)))

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space))) ; FIXMe :keys? shouldnt it be just :space?

(defn- update-game-paused []
  (bind-root #'world.time/paused? (or entity-tick-error
                                      (and pausing?
                                           (player-state-pause-game?)
                                           (not (player-unpaused?)))))
  nil)

(defn- update-world []
  (world.time/update! (min (g/delta-time) entity/max-delta-time))
  (let [entities (content-grid/active-entities)]
    (potential-fields/update! entities)
    (try (entity/tick-entities! entities)
         (catch Throwable t
           (error-window! t)
           (bind-root #'entity-tick-error t))))
  nil)

(defn game-loop []
  (tx/do-all [player-update-state
              mouseover-entity/update! ; this do always so can get debug info even when game not running
              update-game-paused
              #(when-not world.time/paused?
                 (update-world))
              entity/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
              ]))
