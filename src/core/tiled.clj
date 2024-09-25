(ns core.tiled
  (:require [clojure.gdx :refer :all :exclude [visible?]]
            [data.grid2d :as grid])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion
           (com.badlogic.gdx.maps MapLayer MapLayers MapProperties)
           [com.badlogic.gdx.maps.tiled TmxMapLoader TiledMap TiledMapTile TiledMapTileLayer TiledMapTileLayer$Cell]
           com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile
           [gdl OrthogonalTiledMapRenderer ColorSetter]))

(defn load-map
  "Has to be disposed."
  [file]
  (.load (TmxMapLoader.) file))

; implemented by: TiledMap, TiledMapTile, TiledMapTileLayer
(defprotocol HasProperties
  (m-props ^MapProperties [_] "Returns instance of com.badlogic.gdx.maps.MapProperties")
  (get-property [_ key] "Pass keyword key, looks up in properties."))

(defprotocol TMap
  (width [_])
  (height [_])
  (layers ^MapLayers [_] "Returns instance of com.badlogic.gdx.maps.MapLayers of the tiledmap")
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

(defn layer-name ^String [layer]
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
  (.get (m-props has-properties) (name key)))

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

(extend-type com.badlogic.gdx.maps.tiled.TiledMap
  TMap
  (width  [tiled-map] (get-property tiled-map :width))
  (height [tiled-map] (get-property tiled-map :height))

  (layers [tiled-map]
    (.getLayers tiled-map))

  (layer-index [tiled-map layer]
    (let [idx (.getIndex (layers tiled-map) (layer-name layer))]
      (when-not (= idx -1)
        idx)))

  (get-layer [tiled-map layer-name]
    (.get (layers tiled-map) ^String layer-name))

  (remove-layer! [tiled-map layer]
    (.remove (layers tiled-map)
             (int (layer-index tiled-map layer))))

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
; lazy seqs??

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

; OrthogonalTiledMapRenderer extends BatchTiledMapRenderer
; and when a batch is passed to the constructor
; we do not need to dispose the renderer
(defn- map-renderer-for [{:keys [batch] :as g} tiled-map]
  (OrthogonalTiledMapRenderer. tiled-map (float (world-unit-scale g)) batch))

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
         layers
         (filter MapLayer/.isVisible)
         (map (partial layer-index tiled-map))
         int-array
         (.render map-renderer))))

; "Tiles are usually shared by multiple cells."
; https://libgdx.com/wiki/graphics/2d/tile-maps#cells
; No copied-tile for AnimatedTiledMapTile yet (there was no copy constructor/method)
(def ^:private copy-tile
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

(defn- cell->tile [cell]
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

(defn- ->empty-tiled-map []
  (TiledMap.))

(defn put! [^MapProperties properties key value]
  (.put properties key value))

(defn- put-all! [^MapProperties properties other-properties]
  (.putAll properties other-properties))

(defn- visible? [^TiledMapTileLayer layer]
  (.isVisible layer))

(defn grid->tiled-map
  "Creates an empty new tiled-map with same layers and properties as schema-tiled-map.
  The size of the map is as of the grid, which contains also the tile information from the schema-tiled-map."
  [schema-tiled-map grid]
  (let [tiled-map (->empty-tiled-map)
        properties (m-props tiled-map)]
    (put-all! properties (m-props schema-tiled-map))
    (put! properties "width"  (grid/width  grid))
    (put! properties "height" (grid/height grid))
    (doseq [layer (layers schema-tiled-map)
            :let [new-layer (add-layer! tiled-map
                                        :name (layer-name layer)
                                        :visible (visible? layer)
                                        :properties (m-props layer))]]
      (doseq [position (grid/posis grid)
              :let [local-position (get grid position)]
              :when local-position]
        (when (vector? local-position)
          (when-let [cell (cell-at schema-tiled-map layer local-position)]
            (set-tile! new-layer
                       position
                       (copy-tile (cell->tile cell)))))))
    tiled-map))

(defn wgt-grid->tiled-map [grid position->tile]
  (let [tiled-map (->empty-tiled-map)
        properties (m-props tiled-map)]
    (put! properties "width"  (grid/width  grid))
    (put! properties "height" (grid/height grid))
    (put! properties "tilewidth" 48)
    (put! properties "tileheight" 48)
    (let [layer (add-layer! tiled-map :name "ground" :visible true)
          properties (m-props layer)]
      (put! properties "movement-properties" true)
      (doseq [position (grid/posis grid)
              :let [value (get grid position)
                    cell (cell-at tiled-map layer position)]]
        (set-tile! layer position (position->tile position))))
    tiled-map))
