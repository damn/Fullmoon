(ns core.world.explored-tile-corners
  (:require [data.grid2d :as grid2d]
            [core.ctx :refer :all]))

(defcomponent :context/explored-tile-corners
  (->mk [_ {:keys [context/grid]}]
    (atom (grid2d/create-grid (grid2d/width grid)
                              (grid2d/height grid)
                              (constantly false)))))

; TODO put tile param
(defn explored? [ctx position]
  (get @(:context/explored-tile-corners ctx) position))
