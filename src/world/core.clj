(ns world.core
  (:require [world.raycaster :as raycaster]))

(declare player
         tiled-map
         explored-tile-corners
         entity-tick-error
         ^:private raycaster)

(defn ray-blocked? [start target]
  (raycaster/blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [start target path-w]
  (raycaster/path-blocked? raycaster start target path-w))

(defn init-raycaster! [grid position->blocked?]
  (def raycaster (raycaster/create grid position->blocked?)))
