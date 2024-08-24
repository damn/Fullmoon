(ns core.maps.tiled
  (:require [gdx.maps.properties :as properties]
            [gdx.maps.layers :as map-layers])
  (:import com.badlogic.gdx.maps.MapLayer
           [com.badlogic.gdx.maps.tiled TiledMap TiledMapTile TiledMapTileLayer TiledMapTileLayer$Cell]))

; implemented by: TiledMap, TiledMapTile, TiledMapTileLayer
(defprotocol HasProperties
  (properties [_] "Returns instance of com.badlogic.gdx.maps.MapProperties")
  (get-property [_ key] "Pass keyword key, looks up in properties."))

(defprotocol TMap
  (width [_])
  (height [_])
  (layers [_] "Returns instance of com.badlogic.gdx.maps.MapLayers of the tiledmap")
  (layer-index [_ layer]
               "Returns nil or the integer index of the layer.
               Layer can be keyword or an instance of TiledMapTileLayer.")
  (get-layer [_ layer-name]
             "Returns the layer with name (string).")
  (remove-layer! [_ layer]
                 "Removes the layer, layer can be keyword or an actual layer object.")
  (cell-at [_ layer position] "Layer can be keyword or layer object.
                              Position vector [x y].
                              If the layer is part of tiledmap, returns the TiledMapTileLayer$Cell at position.")
  (property-value [_ layer position property-key]
                  "Returns the property value of the tile at the cell in layer.
                  If there is no cell at this position in the layer returns :no-cell.
                  If the property value is undefined returns :undefined.
                  Layer is keyword or layer object.")
  (map-positions [_] "Returns a sequence of all [x y] positions in the tiledmap.")
  (positions-with-property [_ layer property-key]
                           "If the layer (keyword or layer object) does not exist returns nil.
                           Otherwise returns a sequence of [[x y] value] for all tiles who have property-key."))

(defn layer-name [layer]
  (if (keyword? layer)
    (name layer)
    (.getName ^MapLayer layer)))

(comment
 ; could do this but slow -> fetch directly necessary properties
 (defn properties [obj]
   (let [^MapProperties ps (.getProperties obj)]
     (zipmap (map keyword (.getKeys ps)) (.getValues ps))))

 )

(defn- lookup [has-properties key]
  (properties/get (properties has-properties) (name key)))

(extend-protocol HasProperties
  TiledMap
  (properties [tiled-map] (.getProperties tiled-map))
  (get-property [tiled-map key] (lookup tiled-map key))

  MapLayer
  (properties [layer] (.getProperties layer))
  (get-property [layer key] (lookup layer key))

  TiledMapTile
  (properties [tile] (.getProperties tile))
  (get-property [tile key] (lookup tile key)))

(extend-type com.badlogic.gdx.maps.tiled.TiledMap
  TMap
  (width  [tiled-map] (get-property tiled-map :width))
  (height [tiled-map] (get-property tiled-map :height))

  (layers [tiled-map]
    (.getLayers tiled-map))

  (layer-index [tiled-map layer]
    (map-layers/index (layers tiled-map)
                      (layer-name layer)))

  (get-layer [tiled-map layer-name]
    (map-layers/get (layers tiled-map) layer-name))

  (remove-layer! [tiled-map layer]
    (map-layers/remove! (layers tiled-map)
                        (layer-index tiled-map layer)))

  (cell-at [tiled-map layer [x y]]
    (when-let [layer (get-layer tiled-map (layer-name layer))]
      (.getCell ^TiledMapTileLayer layer x y)))

  ; we want cell property not tile property
  ; so why care for no-cell ? just return nil
  (property-value [tiled-map layer position property-key]
    (assert (keyword? property-key))
    (if-let [cell (cell-at tiled-map layer position)]
      (if-let [value (get-property (.getTile ^TiledMapTileLayer$Cell cell) property-key)]
        value
        :undefined)
      :no-cell))

  (map-positions [tiled-map]
    (for [x (range (width  tiled-map))
          y (range (height tiled-map))]
      [x y]))

  (positions-with-property [tiled-map layer property-key]
    (when (layer-index tiled-map layer)
      (for [position (map-positions tiled-map)
            :let [[x y] position
                  value (property-value tiled-map layer position property-key)]
            :when (not (#{:undefined :no-cell} value))]
        [position value]))))

; TODO performance bottleneck -> every time getting same layers
; takes 600 ms to read movement-properties

(defn- tile-movement-property [tiled-map layer position]
  (let [value (property-value tiled-map layer position :movement)]
    (assert (not= value :undefined)
            (str "Value for :movement at position "
                 position  " / mapeditor inverted position: " [(position 0)
                                                               (- (dec (height tiled-map))
                                                                  (position 1))]
                 " and layer " (layer-name layer) " is undefined."))
    (when-not (= :no-cell value)
      value)))

(defn- movement-property-layers [tiled-map]
  (filter #(get-property % :movement-properties)
          (reverse
           (layers tiled-map))))

(defn movement-properties [tiled-map position]
  (for [layer (movement-property-layers tiled-map)]
    [(layer-name layer)
     (tile-movement-property tiled-map layer position)]))

(defn movement-property [tiled-map position]
  (or (->> tiled-map
           movement-property-layers
           (some #(tile-movement-property tiled-map % position)))
      "none"))
