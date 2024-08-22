(ns context.tiled
  (:require [gdx.maps.map-renderer :as map-renderer]
            [gdx.maps.map-layer :as map-layer]
            [gdx.maps.map-layers :as map-layers]
            [gdx.maps.map-properties :as properties]
            [gdx.maps.tiled.tmx-map-loader :refer [->tmx-map-loader] :as tmx-map-loader]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.graphics :as g]
            [api.maps.tiled :as tiled])
  (:import com.badlogic.gdx.maps.MapLayer
           [com.badlogic.gdx.maps.tiled TiledMap TiledMapTile TiledMapTileLayer TiledMapTileLayer$Cell]
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

(defcomponent :context/tiled {}
  (component/create [_ _ctx]
    (memoize map-renderer-for)))

(extend-type api.context.Context
  api.context/TiledMapLoader
  (->tiled-map [_ file]
    (tmx-map-loader/load (->tmx-map-loader) file))

  (render-tiled-map [{g :context/graphics
                      cached-map-renderer :context/tiled
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

(comment
 ; could do this but slow -> fetch directly necessary properties
 (defn properties [obj]
   (let [^MapProperties ps (.getProperties obj)]
     (zipmap (map keyword (.getKeys ps)) (.getValues ps))))

 )

(defn- lookup [has-properties key]
  (properties/get (tiled/properties has-properties) (name key)))

(extend-protocol api.maps.tiled/HasProperties
  TiledMap
  (properties [tiled-map] (.getProperties tiled-map))
  (get-property [tiled-map key] (lookup tiled-map key))

  MapLayer
  (properties [layer] (.getProperties layer))
  (get-property [layer key] (lookup layer key))

  TiledMapTile
  (properties [tile] (.getProperties tile))
  (get-property [tile key] (lookup tile key)))

(extend-type TiledMap
  api.maps.tiled/TiledMap
  (width  [tiled-map] (tiled/get-property tiled-map :width))
  (height [tiled-map] (tiled/get-property tiled-map :height))

  (layers [tiled-map]
    (.getLayers tiled-map))

  (layer-index [tiled-map layer]
    (map-layers/index (tiled/layers tiled-map)
                      (tiled/layer-name layer)))

  (get-layer [tiled-map layer-name]
    (map-layers/get (tiled/layers tiled-map) layer-name))

  (remove-layer! [tiled-map layer]
    (map-layers/remove! (tiled/layers tiled-map)
                        (tiled/layer-index tiled-map layer)))

  (cell-at [tiled-map layer [x y]]
    (when-let [layer (tiled/get-layer tiled-map (tiled/layer-name layer))]
      (.getCell ^TiledMapTileLayer layer x y)))

  ; we want cell property not tile property
  ; so why care for no-cell ? just return nil
  (property-value [tiled-map layer position property-key]
    (assert (keyword? property-key))
    (if-let [cell (tiled/cell-at tiled-map layer position)]
      (if-let [value (tiled/get-property (.getTile ^TiledMapTileLayer$Cell cell) property-key)]
        value
        :undefined)
      :no-cell))

  (map-positions [tiled-map]
    (for [x (range (tiled/width  tiled-map))
          y (range (tiled/height tiled-map))]
      [x y]))

  (positions-with-property [tiled-map layer property-key]
    (when (tiled/layer-index tiled-map layer)
      (for [position (tiled/map-positions tiled-map)
            :let [[x y] position
                  value (tiled/property-value tiled-map layer position property-key)]
            :when (not (#{:undefined :no-cell} value))]
        [position value]))))
