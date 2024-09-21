(ns core.context.tiled-map-renderer
  (:require [gdx.maps.tiled :as tiled]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.g :as g])
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
  (component/create [_ _ctx]
    (memoize map-renderer-for)))

(extend-type core.context.Context
  core.context/TiledMapDrawer
  (render-tiled-map [{g :context/graphics
                      cached-map-renderer :context/tiled-map-renderer
                      :as ctx}
                     tiled-map
                     color-setter]
    (let [^OrthogonalTiledMapRenderer map-renderer (cached-map-renderer g tiled-map)
          world-camera (ctx/world-camera ctx)]
      (set-color-setter! map-renderer color-setter)
      (.setView map-renderer world-camera)
      (->> tiled-map
           tiled/layers
           (filter MapLayer/.isVisible)
           (map (partial tiled/layer-index tiled-map))
           int-array
           (.render map-renderer)))))
