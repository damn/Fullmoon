(ns graphics.tiled-map-drawer
  (:require api.graphics
            [api.maps.tiled :as tiled])
  (:import com.badlogic.gdx.graphics.OrthographicCamera
           [com.badlogic.gdx.maps MapRenderer MapLayer]
           [gdl OrthogonalTiledMapRendererWithColorSetter ColorSetter]))

; OrthogonalTiledMapRenderer extends BatchTiledMapRenderer
; and when a batch is passed to the constructor
; we do not need to dispose the renderer
(defn- map-renderer-for [{:keys [batch world-unit-scale]}
                         tiled-map
                         color-setter]
  (OrthogonalTiledMapRendererWithColorSetter. tiled-map
                                              (float world-unit-scale)
                                              batch
                                              (reify ColorSetter
                                                (apply [_ color x y]
                                                  (color-setter color x y)))))

(defn ->build []
  {:cached-map-renderer (memoize map-renderer-for)})

(extend-type api.graphics.Graphics
  api.graphics/TiledMapRenderer
  (render-tiled-map [{:keys [world-camera cached-map-renderer] :as g}
                     tiled-map
                     color-setter]
    (let [^MapRenderer map-renderer (cached-map-renderer g tiled-map color-setter)]
      (.update ^OrthographicCamera world-camera)
      (.setView map-renderer world-camera)
      (->> tiled-map
           tiled/layers
           (filter #(.isVisible ^MapLayer %))
           (map (partial tiled/layer-index tiled-map))
           int-array
           (.render map-renderer)))))
