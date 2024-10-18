(ns gdx.graphics.tiled
  (:require [gdx.tiled :as tiled])
  (:import (gdl OrthogonalTiledMapRenderer ColorSetter)))

(defn renderer
  "OrthogonalTiledMapRenderer extends BatchTiledMapRenderer
  and when a batch is passed to the constructor
  we do not need to dispose the renderer"
  [tiled-map unit-scale batch]
  (OrthogonalTiledMapRenderer. tiled-map (float unit-scale) batch))

(defn render
  "Renders tiled-map using camera position and with unit-scale.
  Color-setter is a (fn [color x y]) which is called for every tile-corner to set the color.
  Can be used for lights & shadows.
  Renders only visible layers."
  [^OrthogonalTiledMapRenderer map-renderer color-setter camera tiled-map]
  (.setColorSetter map-renderer (reify ColorSetter
                                  (apply [_ color x y]
                                    (color-setter color x y))))
  (.setView map-renderer camera)
  (->> tiled-map
       tiled/layers
       (filter tiled/visible?)
       (map (partial tiled/layer-index tiled-map))
       int-array
       (.render map-renderer)))
