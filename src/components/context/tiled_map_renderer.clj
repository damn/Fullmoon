(ns components.context.tiled-map-renderer
  (:require [gdx.maps.map-renderer :as map-renderer]
            [gdx.maps.map-layer :as map-layer]
            [gdx.maps.tiled.tmx-map-loader :refer [->tmx-map-loader] :as tmx-map-loader]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.graphics :as g]
            [core.maps.tiled :as tiled])
  (:import [gdl OrthogonalTiledMapRenderer ColorSetter]))

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

(defcomponent :context/tiled-map-renderer {}
  (component/create [_ _ctx]
    (memoize map-renderer-for)))

(extend-type core.context.Context
  core.context/TiledMapLoader
  (->tiled-map [_ file]
    (tmx-map-loader/load (->tmx-map-loader) file))

  (render-tiled-map [{g :context/graphics
                      cached-map-renderer :context/tiled-map-renderer
                      :as ctx}
                     tiled-map
                     color-setter]
    (let [map-renderer (cached-map-renderer g tiled-map)
          world-camera (ctx/world-camera ctx)]
      (set-color-setter! map-renderer color-setter)
      (map-renderer/set-view! map-renderer world-camera)
      (->> tiled-map
           tiled/layers
           (filter map-layer/visible?)
           (map (partial tiled/layer-index tiled-map))
           (map-renderer/render map-renderer)))))
