(ns core.ctx.tiled-map-renderer
  (:require [core.tiled :as tiled]
            [core.ctx :refer :all]
            [core.graphics.views :refer [world-camera]]
            [core.graphics :as g])
  (:import com.badlogic.gdx.maps.MapLayer
           [gdl OrthogonalTiledMapRenderer ColorSetter]))

; OrthogonalTiledMapRenderer extends BatchTiledMapRenderer
; and when a batch is passed to the constructor
; we do not need to dispose the renderer
(defn- map-renderer-for [{:keys [batch] :as g} tiled-map]
  (OrthogonalTiledMapRenderer. tiled-map
                               (float (g/world-unit-scale g))
                               batch))

(defn- set-color-setter! [^OrthogonalTiledMapRenderer map-renderer color-setter]
  (.setColorSetter map-renderer (reify ColorSetter
                                  (apply [_ color x y]
                                    (color-setter color x y)))))

(defcomponent :context/tiled-map-renderer
  {:data :some}
  (->mk [_ _ctx]
    (memoize map-renderer-for)))

(defn render!
  "Renders tiled-map using world-view at world-camera position and with world-unit-scale.
Color-setter is a gdl.ColorSetter which is called for every tile-corner to set the color.
Can be used for lights & shadows.
The map-renderers are created and cached internally.
Renders only visible layers."
  [{g :context/graphics cached-map-renderer :context/tiled-map-renderer :as ctx}
   tiled-map
   color-setter]
  (let [^OrthogonalTiledMapRenderer map-renderer (cached-map-renderer g tiled-map)
        world-camera (world-camera ctx)]
    (set-color-setter! map-renderer color-setter)
    (.setView map-renderer world-camera)
    (->> tiled-map
         tiled/layers
         (filter MapLayer/.isVisible)
         (map (partial tiled/layer-index tiled-map))
         int-array
         (.render map-renderer))))
