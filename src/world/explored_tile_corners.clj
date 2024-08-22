(ns world.explored-tile-corners
  (:require [data.grid2d :as grid2d]
            [core.component :as component :refer [defcomponent]]
            api.context))

(defcomponent :world/explored-tile-corners {}
  (component/create [_ {:keys [world/grid]}]
    (atom (grid2d/create-grid (grid2d/width grid)
                              (grid2d/height grid)
                              (constantly false)))))

(extend-type api.context.Context
  api.context/ExploredTileCorners
  ; TODO put tile param
  (explored? [ctx position]
    (get @(:world/explored-tile-corners ctx) position)))
