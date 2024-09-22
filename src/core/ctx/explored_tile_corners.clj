(ns core.ctx.explored-tile-corners
  (:require [data.grid2d :as grid2d]
            [core.component :as component :refer [defcomponent]]))

(defcomponent :context/explored-tile-corners
  (component/create [_ {:keys [context/grid]}]
    (atom (grid2d/create-grid (grid2d/width grid)
                              (grid2d/height grid)
                              (constantly false)))))

; TODO put tile param
(defn explored? [ctx position]
  (get @(:context/explored-tile-corners ctx) position))
