(ns gdl.maps.tiled
  (:import com.badlogic.gdx.maps.MapLayer))

; implemented by: TiledMap, TiledMapTile, TiledMapTileLayer -> protocol docstring.
(defprotocol HasProperties
  (properties [_] "Returns instance of com.badlogic.gdx.maps.MapProperties")
  ; just 'property' ?
  (get-property [_ key] "Pass keyword key, looks up in properties."))

(defprotocol TiledMap
  (width [_])
  (height [_])
  (layers [_] "Returns instance of com.badlogic.gdx.maps.MapLayers of the tiledmap")
  ; these 3 are actually MapLayers object, separate.=> wrong abstraction ?
  (layer-index [_ layer]
               "Returns nil or the integer index of the layer.
               Layer can be keyword or an instance of TiledMapTileLayer.")
  (get-layer [_ layer-name]
             "Returns the layer with name (string).") ; keyword ?
  (remove-layer! [_ layer]
                 "Removes the layer, layer can be keyword or an actual layer object.")
  ; cell is called on layer .. ? => wrong abstraction ?
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
