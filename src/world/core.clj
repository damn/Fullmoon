(ns world.core
  (:require [data.grid2d :as g2d]
            [world.content-grid :as content-grid]
            [world.raycaster :as raycaster]))

(declare paused?)

(declare ^{:doc "The game logic update delta-time. Different then clojure.gdx.graphics/delta-time because it is bounded by a maximum value for entity movement speed."}
         world-delta

         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time

         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defn init-time! []
  (.bindRoot #'elapsed-time 0)
  (.bindRoot #'logic-frame 0))

(defn update-time! [delta]
  (.bindRoot #'world-delta delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defn ->counter [duration]
  {:pre [(>= duration 0)]}
  {:duration duration
   :stop-time (+ elapsed-time duration)})

(defn stopped? [{:keys [stop-time]}]
  (>= elapsed-time stop-time))

(defn reset [{:keys [duration] :as counter}]
  (assoc counter :stop-time (+ elapsed-time duration)))

(defn finished-ratio [{:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time elapsed-time) duration))))

(declare player
         tiled-map
         entity-tick-error)

(declare explored-tile-corners)

(defn init-explored-tile-corners! [width height]
  (.bindRoot #'explored-tile-corners (atom (g2d/create-grid width height (constantly false)))))

(declare ^:private raycaster)

(defn init-raycaster! [grid position->blocked?]
  (.bindRoot #'raycaster (raycaster/create grid position->blocked?)))

(defn ray-blocked? [start target]
  (raycaster/blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [start target path-w]
  (raycaster/path-blocked? raycaster start target path-w))

(declare ^:private content-grid)

(defn init-content-grid! [opts]
  (.bindRoot #'content-grid (content-grid/create opts)))

(defn active-entities []
  (content-grid/active-entities content-grid @player))

(defn add-entity! [eid]
  (content-grid/update-entity! content-grid eid))

(defn remove-entity! [eid]
  (content-grid/remove-entity! eid))

(defn update-entity! [eid]
  (content-grid/update-entity! content-grid eid))

(declare widgets)
