(ns gdx.tiled
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion
           (com.badlogic.gdx.maps MapLayer MapLayers MapProperties)
           (com.badlogic.gdx.maps.tiled TmxMapLoader TiledMap TiledMapTile TiledMapTileLayer TiledMapTileLayer$Cell)
           com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile))

(defn load-map
  "Has to be disposed."
  [file]
  (.load (TmxMapLoader.) file))

(defprotocol HasProperties
  (m-props ^MapProperties [_] "Returns instance of com.badlogic.gdx.maps.MapProperties")
  (get-property [_ key] "Pass keyword key, looks up in properties."))

(defn layer-name ^String [layer]
  (if (keyword? layer)
    (name layer)
    (.getName ^MapLayer layer)))

(defn- lookup [has-properties key]
  (.get (m-props has-properties) (name key)))

(comment
 ; could do this but slow -> fetch directly necessary properties
 (defn properties [obj]
   (let [^MapProperties ps (.getProperties obj)]
     (zipmap (map keyword (.getKeys ps)) (.getValues ps))))
 )

(extend-protocol HasProperties
  TiledMap
  (m-props [tiled-map] (.getProperties tiled-map))
  (get-property [tiled-map key] (lookup tiled-map key))
  MapLayer
  (m-props [layer] (.getProperties layer))
  (get-property [layer key] (lookup layer key))
  TiledMapTile
  (m-props [tile] (.getProperties tile))
  (get-property [tile key] (lookup tile key)))

(defn width  [tiled-map] (get-property tiled-map :width))
(defn height [tiled-map] (get-property tiled-map :height))

(defn layers ^MapLayers [tiled-map] (.getLayers tiled-map))

(defn layer-index
  "Returns nil or the integer index of the layer.
  Layer can be keyword or an instance of TiledMapTileLayer."
  [tiled-map layer]
  (let [idx (.getIndex (layers tiled-map) (layer-name layer))]
    (when-not (= idx -1)
      idx)))

(defn get-layer
  "Returns the layer with name (string)."
  [tiled-map layer-name]
  (.get (layers tiled-map) ^String layer-name))

(defn remove-layer!
  "Removes the layer, layer can be keyword or an actual layer object."
  [tiled-map layer]
  (.remove (layers tiled-map)
           (int (layer-index tiled-map layer))))

(defn cell-at
  "Layer can be keyword or layer object.
  Position vector [x y].
  If the layer is part of tiledmap, returns the TiledMapTileLayer$Cell at position."
  [tiled-map layer [x y]]
  (when-let [layer (get-layer tiled-map (layer-name layer))]
    (.getCell ^TiledMapTileLayer layer x y)))

(defn property-value
  "Returns the property value of the tile at the cell in layer.
  If there is no cell at this position in the layer returns :no-cell.
  If the property value is undefined returns :undefined.
  Layer is keyword or layer object."
  [tiled-map layer position property-key]
  (assert (keyword? property-key))
  (if-let [cell (cell-at tiled-map layer position)]
    (if-let [value (get-property (.getTile ^TiledMapTileLayer$Cell cell) property-key)]
      value
      :undefined)
    :no-cell))

(defn map-positions
  "Returns a sequence of all [x y] positions in the tiledmap."
  [tiled-map]
  (for [x (range (width  tiled-map))
        y (range (height tiled-map))]
    [x y]))

(defn positions-with-property
  "If the layer (keyword or layer object) does not exist returns nil.
  Otherwise returns a sequence of [[x y] value] for all tiles who have property-key."
  [tiled-map layer property-key]
  (when (layer-index tiled-map layer)
    (for [position (map-positions tiled-map)
          :let [[x y] position
                value (property-value tiled-map layer position property-key)]
          :when (not (#{:undefined :no-cell} value))]
      [position value])))

(def copy-tile
  "Memoized function.
  Tiles are usually shared by multiple cells.
  https://libgdx.com/wiki/graphics/2d/tile-maps#cells
  No copied-tile for AnimatedTiledMapTile yet (there was no copy constructor/method)"
  (memoize
   (fn [^StaticTiledMapTile tile]
     (assert tile)
     (StaticTiledMapTile. tile))))

(defn ->static-tiled-map-tile [texture-region]
  (assert texture-region)
  (StaticTiledMapTile. ^TextureRegion texture-region))

(defn set-tile! [^TiledMapTileLayer layer [x y] tile]
  (let [cell (TiledMapTileLayer$Cell.)]
    (.setTile cell tile)
    (.setCell layer x y cell)))

(defn cell->tile [cell]
  (.getTile ^TiledMapTileLayer$Cell cell))

(defn add-layer! [tiled-map & {:keys [name visible properties]}]
  (let [layer (TiledMapTileLayer. (width  tiled-map)
                                  (height tiled-map)
                                  (get-property tiled-map :tilewidth)
                                  (get-property tiled-map :tileheight))]
    (.setName layer name)
    (when properties
      (.putAll ^MapProperties (m-props layer) properties))
    (.setVisible layer visible)
    (.add ^MapLayers (layers tiled-map) layer)
    layer))

(defn ->empty-tiled-map [] (TiledMap.))

(defn put! [^MapProperties properties key value]
  (.put properties key value))

(defn put-all! [^MapProperties properties other-properties]
  (.putAll properties other-properties))

(defn visible? [^TiledMapTileLayer layer]
  (.isVisible layer))
