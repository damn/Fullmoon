(ns level.tiled
  (:require [data.grid2d :as g]
            [gdx.tiled :as t]))

; TODO performance bottleneck -> every time getting same layers
; takes 600 ms to read movement-properties
; lazy seqs??

(defn- tile-movement-property [tiled-map layer position]
  (let [value (t/property-value tiled-map layer position :movement)]
    (assert (not= value :undefined)
            (str "Value for :movement at position "
                 position  " / mapeditor inverted position: " [(position 0)
                                                               (- (dec (t/height tiled-map))
                                                                  (position 1))]
                 " and layer " (t/layer-name layer) " is undefined."))
    (when-not (= :no-cell value)
      value)))

(defn- movement-property-layers [tiled-map]
  (filter #(t/get-property % :movement-properties)
          (reverse
           (t/layers tiled-map))))

(defn movement-properties [tiled-map position]
  (for [layer (movement-property-layers tiled-map)]
    [(t/layer-name layer)
     (tile-movement-property tiled-map layer position)]))

(defn movement-property [tiled-map position]
  (or (->> tiled-map
           movement-property-layers
           (some #(tile-movement-property tiled-map % position)))
      "none"))

(defn grid->tiled-map
  "Creates an empty new tiled-map with same layers and properties as schema-tiled-map.
  The size of the map is as of the grid, which contains also the tile information from the schema-tiled-map."
  [schema-tiled-map grid]
  (let [tiled-map (t/->empty-tiled-map)
        properties (t/m-props tiled-map)]
    (t/put-all! properties (t/m-props schema-tiled-map))
    (t/put! properties "width"  (g/width  grid))
    (t/put! properties "height" (g/height grid))
    (doseq [layer (t/layers schema-tiled-map)
            :let [new-layer (t/add-layer! tiled-map
                                          :name (t/layer-name layer)
                                          :visible (t/visible? layer)
                                          :properties (t/m-props layer))]]
      (doseq [position (g/posis grid)
              :let [local-position (get grid position)]
              :when local-position]
        (when (vector? local-position)
          (when-let [cell (t/cell-at schema-tiled-map layer local-position)]
            (t/set-tile! new-layer
                         position
                         (t/copy-tile (t/cell->tile cell)))))))
    tiled-map))

(defn wgt-grid->tiled-map [grid position->tile]
  (let [tiled-map (t/->empty-tiled-map)
        properties (t/m-props tiled-map)]
    (t/put! properties "width"  (g/width  grid))
    (t/put! properties "height" (g/height grid))
    (t/put! properties "tilewidth" 48)
    (t/put! properties "tileheight" 48)
    (let [layer (t/add-layer! tiled-map :name "ground" :visible true)
          properties (t/m-props layer)]
      (t/put! properties "movement-properties" true)
      (doseq [position (g/posis grid)
              :let [value (get grid position)
                    cell (t/cell-at tiled-map layer position)]]
        (t/set-tile! layer position (position->tile position))))
    tiled-map))
