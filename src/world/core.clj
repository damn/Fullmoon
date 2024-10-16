(ns world.core
  (:require [world.content-grid :as content-grid]
            [world.raycaster :as raycaster]))

(declare player
         tiled-map
         explored-tile-corners
         entity-tick-error)

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
