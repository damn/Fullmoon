(ns core.world
  (:require [clojure.world :refer :all]
            [clojure.string :as str]
            [data.grid2d :as g])
  (:import java.util.Random
           com.badlogic.gdx.Input$Keys
           com.badlogic.gdx.graphics.Color
           com.badlogic.gdx.graphics.g2d.TextureRegion
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
    (put! properties "width"  (g/width  grid))
    (put! properties "height" (g/height grid))
    (doseq [layer (layers schema-tiled-map)
            :let [new-layer (add-layer! tiled-map
                                        :name (layer-name layer)
                                        :visible (visible? layer)
                                        :properties (m-props layer))]]
      (doseq [position (g/posis grid)
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
    (put! properties "width"  (g/width  grid))
    (put! properties "height" (g/height grid))
    (put! properties "tilewidth" 48)
    (put! properties "tileheight" 48)
    (let [layer (add-layer! tiled-map :name "ground" :visible true)
          properties (m-props layer)]
      (put! properties "movement-properties" true)
      (doseq [position (g/posis grid)
              :let [value (get grid position)
                    cell (cell-at tiled-map layer position)]]
        (set-tile! layer position (position->tile position))))
    tiled-map))

(defn assoc-ks [m ks v]
  (if (empty? ks)
    m
    (apply assoc m (interleave ks (repeat v)))))

(defn scale-grid [grid [w h]]
  (g/create-grid (* (g/width grid)  w)
                 (* (g/height grid) h)
                 (fn [[x y]]
                   (get grid
                        [(int (/ x w))
                         (int (/ y h))]))))

(defn scalegrid [grid factor]
  (g/create-grid (* (g/width grid) factor)
                 (* (g/height grid) factor)
                 (fn [posi]
                   (get grid (mapv #(int (/ % factor)) posi)))))
; TODO other keys in the map-grid are lost -> look where i transform grids like this
; merge with ld-grid?

(defn create-borders-positions [grid] ; TODO not distinct -> apply distinct or set
  (let [w (g/width grid),h (g/height grid)]
    (concat
      (mapcat (fn [x] [[x 0] [x (dec h)]]) (range w))
      (mapcat (fn [y] [[0 y] [(dec w) y]]) (range h)))))

(defn get-3x3-cellvalues [grid posi]
  (map grid (cons posi (g/get-8-neighbour-positions posi))))

(defn not-border-position? [[x y] grid]
  (and (>= x 1) (>= y 1)
       (< x (dec (g/width grid)))
       (< y (dec (g/height grid)))))

(defn border-position? [p grid] (not (not-border-position? p grid)))

(defn wall-at? [grid posi]
  (= :wall (get grid posi)))

(defn undefined-value-behind-walls
  "also border positions set to undefined where there are nil values"
  [grid]
  (g/transform grid
               (fn [posi value]
                 (if (and (= :wall value)
                          (every? #(let [value (get grid %)]
                                     (or (= :wall value) (nil? value)))
                                  (g/get-8-neighbour-positions posi)))
                   :undefined
                   value))))

; if no tile
; and some has tile at get-8-neighbour-positions
; -> should be a wall
; -> paint in tiled editor set tile at cell and layer
; -> texture ?
; spritesheet already available ?!

(defn fill-single-cells [grid] ; TODO removes single walls without adjacent walls
  (g/transform grid
               (fn [posi value]
                 (if (and (not-border-position? posi grid)
                          (= :wall value)
                          (not-any? #(wall-at? grid %)
                                    (g/get-4-neighbour-positions posi)))
                   :ground
                   value))))

(defn- print-cell [celltype]
  (print (if (number? celltype)
           celltype
           (case celltype
             nil               "?"
             :undefined        " "
             :ground           "_"
             :wall             "#"
             :airwalkable      "."
             :module-placement "X"
             :start-module     "@"
             :transition       "+"))))

; print-grid in data.grid2d is y-down
(defn printgrid
  "Prints with y-up coordinates."
  [grid]
  (doseq [y (range (dec (g/height grid)) -1 -1)]
    (doseq [x (range (g/width grid))]
      (print-cell (grid [x y])))
    (println)))

(let [idxvalues-order [[1 0] [-1 0] [0 1] [0 -1]]]
  (assert (= (g/get-4-neighbour-positions [0 0])
             idxvalues-order)))

(comment
  ; Values for every neighbour:
  {          [0 1] 1
   [-1 0]  8          [1 0] 2
             [0 -1] 4 })

; so the idxvalues-order corresponds to the following values for a neighbour tile:
(def ^:private idxvalues [2 8 1 4])

(defn- calculate-index-value [position->transition? idx position]
  (if (position->transition? position)
    (idxvalues idx)
    0))

(defn transition-idx-value [position position->transition?]
  (->> position
       g/get-4-neighbour-positions
       (map-indexed (partial calculate-index-value
                             position->transition?))
       (apply +)))

(defn- nad-corner? [grid [fromx fromy] [tox toy]]
  (and
    (= :ground (get grid [tox toy])) ; also filters nil/out of map
    (wall-at? grid [tox fromy])
    (wall-at? grid [fromx toy])))

(def ^:private diagonal-steps [[-1 -1] [-1 1] [1 -1] [1 1]])

; TODO could be made faster because accessing the same posis oftentimes at nad-corner? check
(defn get-nads [grid]
  (loop [checkposis (filter (fn [{y 1 :as posi}]
                              (and (even? y)
                                   (= :ground (get grid posi))))
                            (g/posis grid))
         result []]
    (if (seq checkposis)
      (let [position (first checkposis)
            diagonal-posis (map #(mapv + position %) diagonal-steps)
            nads (map (fn [nad] [position nad])
                      (filter #(nad-corner? grid position %) diagonal-posis))]
        (recur
          (rest checkposis)
          (doall (concat result nads)))) ; doall else stackoverflow error
      result)))

(defn- get-tiles-needing-fix-for-nad [grid [[fromx fromy]
                                           [tox toy]]]
  (let [xstep (- tox fromx)
        ystep (- toy fromy)
        cell1x (+ fromx xstep)
        cell1y fromy
        cell1 [cell1x cell1y]
        cell11 [(+ cell1x xstep) (+ cell1y (- ystep))]
        cell2x (+ cell1x xstep)
        cell2y cell1y
        cell2 [cell2x cell2y]
        cell21 [(+ cell2x xstep) (+ cell2y ystep)]
        cell3 [cell2x (+ cell2y ystep)]]
;    (println "from: " [fromx fromy] " to: " [tox toy])
;    (println "xstep " xstep " ystep " ystep)
;    (println "cell1 " cell1)
;    (println "cell11 " cell11)
;    (println "cell2 " cell2)
;    (println "cell21 " cell21)
;    (println "cell3 " cell3)
    (if-not (nad-corner? grid cell1 cell11)
      [cell1]
      (if-not (nad-corner? grid cell2 cell21)
        [cell1 cell2]
        [cell1 cell2 cell3]))))

(defn mark-nads [grid nads label]
  (assoc-ks grid (mapcat #(get-tiles-needing-fix-for-nad grid %) nads) label))

(defn fix-not-allowed-diagonals [grid]
  (mark-nads grid (get-nads grid) :ground))

;; TEST

(comment
  (def found (atom false))

  (defn search-buggy-nads []
    (println "searching buggy nads")
    (doseq [n (range 100000)
            :when (not @found)]
      (println "try " n)
      (let [grid (cellular-automata-gridgen 100 80 :fillprob 62 :generations 0 :wall-borders true)
            nads (get-nads grid)
            fixed-grid (mark-nads grid nads :ground)]
        (when
          (and
            (not (zero? (count nads)))
            (not (zero? (count (get-nads fixed-grid)))))
          (println "found!")
          (reset! found [grid fixed-grid]))))
    (println "found buggy nads? " @found)))

(def modules-file "maps/modules.tmx")
(def module-width  32)
(def module-height 20)
(def modules-scale [module-width module-height])

(def ^:private number-modules-x 8)
(def ^:private number-modules-y 4)
(def ^:private module-offset-tiles 1)
(def ^:private transition-modules-row-width 4)
(def ^:private transition-modules-row-height 4)
(def ^:private transition-modules-offset-x 4)
(def ^:private floor-modules-row-width 4)
(def ^:private floor-modules-row-height 4)
(def ^:private floor-idxvalue 0)

(defn- module-index->tiled-map-positions [[module-x module-y]]
  (let [start-x (* module-x (+ module-width  module-offset-tiles))
        start-y (* module-y (+ module-height module-offset-tiles))]
    (for [x (range start-x (+ start-x module-width))
          y (range start-y (+ start-y module-height))]
      [x y])))

(defn- floor->module-index []
  [(rand-int floor-modules-row-width)
   (rand-int floor-modules-row-height)])

(defn- transition-idxvalue->module-index [idxvalue]
  [(+ (rem idxvalue transition-modules-row-width)
      transition-modules-offset-x)
   (int (/ idxvalue transition-modules-row-height))])

(defn- place-module [scaled-grid
                     unscaled-position
                     & {:keys [transition?
                               transition-neighbor?]}]
  (let [idxvalue (if transition?
                   (transition-idx-value unscaled-position transition-neighbor?)
                   floor-idxvalue)
        tiled-map-positions (module-index->tiled-map-positions
                             (if transition?
                               (transition-idxvalue->module-index idxvalue)
                               (floor->module-index)))
        offsets (for [x (range module-width)
                      y (range module-height)]
                  [x y])
        offset->tiled-map-position (zipmap offsets tiled-map-positions)
        scaled-position (mapv * unscaled-position modules-scale)]
    (reduce (fn [grid offset]
              (assoc grid
                     (mapv + scaled-position offset)
                     (offset->tiled-map-position offset)))
            scaled-grid
            offsets)))

(defn place-modules [modules-tiled-map
                     scaled-grid
                     unscaled-grid
                     unscaled-floor-positions
                     unscaled-transition-positions]
  (let [_ (assert (and (= (width modules-tiled-map)
                          (* number-modules-x (+ module-width module-offset-tiles)))
                       (= (height modules-tiled-map)
                          (* number-modules-y (+ module-height module-offset-tiles)))))
        scaled-grid (reduce (fn [scaled-grid unscaled-position]
                              (place-module scaled-grid unscaled-position :transition? false))
                            scaled-grid
                            unscaled-floor-positions)
        scaled-grid (reduce (fn [scaled-grid unscaled-position]
                              (place-module scaled-grid unscaled-position :transition? true
                                            :transition-neighbor? #(#{:transition :wall}
                                                                    (get unscaled-grid %))))
                            scaled-grid
                            unscaled-transition-positions)]
    (grid->tiled-map modules-tiled-map scaled-grid)))


;Cave Algorithmus.
;http://properundead.com/2009/03/cave-generator.html
;http://properundead.com/2009/07/procedural-generation-3-cave-source.html
;http://forums.tigsource.com/index.php?topic=5174.0

(defn- create-order [random]
  (sshuffle (range 4) random))

(defn- get-in-order [v order]
  (map #(get v %) order))

(def ^:private current-order (atom nil))

(def ^:private turn-ratio 0.25)

(defn- create-rand-4-neighbour-posis [posi n random] ; TODO does more than 1 thing
  (when (< (srand random) turn-ratio)
    (reset! current-order (create-order random)))
  (take n
        (get-in-order (g/get-4-neighbour-positions posi)
                      @current-order)))

(defn- get-default-adj-num [open-paths random]
  (if (= open-paths 1)
    (case (int (srand-int 4 random))
      0 1
      1 1
      2 1
      3 2
      1)
    (case (int (srand-int 4 random))
      0 0
      1 1
      2 1
      3 2
      1)))

(defn- get-thin-adj-num [open-paths random]
  (if (= open-paths 1)
    1
    (case (int (srand-int 7 random))
      0 0
      1 2
      1)))

(defn- get-wide-adj-num [open-paths random]
  (if (= open-paths 1)
    (case (int (srand-int 3 random))
      0 1
      2)
    (case (int (srand-int 4 random))
      0 1
      1 2
      2 3
      3 4
      1)))

(def ^:private get-adj-num
  {:wide    get-wide-adj-num
   :thin    get-thin-adj-num    ; h�hle mit breite 1 �berall nur -> turn-ratio verringern besser
   :default get-default-adj-num}) ; etwas breiter als 1 aber immernoch zu d�nn f�r m ein game -> turn-ratio verringern besser

; gute ergebnisse: :wide / 500-4000 max-cells / turn-ratio 0.5
; besser 150x150 anstatt 100x100 w h
; TODO glaubich einziger unterschied noch: openpaths wird bei jeder cell neu berechnet?
; TODO max-tries wenn er nie �ber min-cells kommt? -> im let dazu definieren vlt max 30 sekunden -> in tries umgerechnet??
(defn cave-gridgen [random min-cells max-cells adjnum-type]
  ; move up where its used only
  (reset! current-order (create-order random))
  (let [start [0 0]
        start-grid (assoc {} start :ground) ; grid of posis to :ground or no entry for walls
        finished (fn [grid end cell-cnt]
                   ;(println "Reached cells: " cell-cnt) ; TODO cell-cnt stimmt net genau
                   ; TODO already called there down ... make mincells check there
                   (if (< cell-cnt min-cells)
                     (cave-gridgen random min-cells max-cells adjnum-type) ; recur?
                     (let [[grid convert] (g/mapgrid->vectorgrid grid
                                                                    #(if (nil? %) :wall :ground))]
                       {:grid  grid
                        :start (convert start)
                        :end   (convert end)})))]
    (loop [posi-seq [start]
           grid     start-grid
           cell-cnt 0]
      ; TODO min cells check !?
      (if (>= cell-cnt max-cells)
        (finished grid
                  (last posi-seq)
                  cell-cnt)
        (let [try-carve-posis (create-rand-4-neighbour-posis
                                (last posi-seq) ; TODO take random ! at corner ... hmm
                                ((get-adj-num adjnum-type) (count posi-seq) random)
                                random)
              carve-posis (filter #(nil? (get grid %)) try-carve-posis)
              new-pos-seq (concat (drop-last posi-seq) carve-posis)]
          (if (not-empty new-pos-seq)
            (recur new-pos-seq
                   (if (seq carve-posis)
                     (assoc-ks grid carve-posis :ground)
                     grid)
                   (+ cell-cnt (count carve-posis)))
            ; TODO here min-cells check ?
            (finished grid (last posi-seq) cell-cnt)))))))

; can adjust:
; * split percentage , for higher level areas may scale faster (need to be more careful)
; * not 4 neighbors but just 1 tile randomwalk -> possible to have lvl 9 area next to lvl 1 ?
; * adds metagame to the game , avoid/or fight higher level areas, which areas to go next , etc...
; -> up to the player not step by step level increase like D2
; can not only take first of added-p but multiples also
; can make parameter how fast it scales
; area-level-grid works better with more wide grids
; if the cave is very straight then it is just a continous progression and area-level-grid is useless
(defn ->area-level-grid
  "Expands from start position by adding one random adjacent neighbor.
  Each random walk is a step and is assigned a level as of max-level.
  (Levels are scaled, for example grid has 100 ground cells, so steps would be 0 to 100(99?)
  and max-level will smooth it out over 0 to max-level.
  The point of this is to randomize the levels so player does not have a smooth progression
  but can encounter higher level areas randomly around but there is always a path which goes from
  level 0 to max-level, so the player has to decide which areas to do in which order."
  [& {:keys [grid start max-level walk-on]}]
  (let [maxcount (->> grid
                      g/cells
                      (filter walk-on)
                      count)
        ; -> assume all :ground cells can be reached from start
        ; later check steps count == maxcount assert
        level-step (/ maxcount max-level)
        step->level #(int (Math/ceil (/ % level-step)))
        walkable-neighbours (fn [grid position]
                              (filter #(walk-on (get grid %))
                                      (g/get-4-neighbour-positions position)))]
    (loop [next-positions #{start}
           steps          [[0 start]]
           grid           (assoc grid start 0)]
      (let [next-positions (set
                            (filter #(seq (walkable-neighbours grid %))
                                    next-positions))]
        (if (seq next-positions)
          (let [p (rand-nth (seq next-positions))
                added-p (rand-nth (walkable-neighbours grid p))]
            (if added-p
              (let [area-level (step->level (count steps))]
                (recur (conj next-positions added-p)
                       (conj steps [area-level added-p])
                       (assoc grid added-p area-level)))
              (recur next-positions
                     steps
                     grid)))
          {:steps steps
           :area-level-grid grid})))))

; TODO generates 51,52. not max 50
; TODO can use different turn-ratio/depth/etc. params
; (printgrid (:grid (->cave-grid :size 800)))
(defn- ->cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (cave-gridgen (Random.) size size :wide)
        grid (fix-not-allowed-diagonals grid)]
    (assert (= #{:wall :ground} (set (g/cells grid))))
    {:start start
     :grid grid}))


; TODO HERE
; * unique max 16 modules, not random take @ #'floor->module-index, also special start, end modules, rare modules...

; * at the beginning enemies very close, different area different spawn-rate !

; beginning slow enemies low hp low dmg etc.

; * flood-fill gets 8 neighbour posis -> no NADs on modules ! assert !

; * assuming bottom left in floor module is walkable

; whats the assumption here? => or put extra borders around? / assert!

(defn- adjacent-wall-positions [grid]
  (filter (fn [p] (and (= :wall (get grid p))
                       (some #(= :ground (get grid %))
                             (g/get-8-neighbour-positions p))))
          (g/posis grid)))

(defn- flood-fill [grid start walk-on-position?]
  (loop [next-positions [start]
         filled []
         grid grid]
    (if (seq next-positions)
      (recur (filter #(and (get grid %)
                           (walk-on-position? %))
                     (distinct
                      (mapcat g/get-8-neighbour-positions
                              next-positions)))
             (concat filled next-positions)
             (assoc-ks grid next-positions nil))
      filled)))

(comment
 (let [{:keys [start grid]} (->cave-grid :size 15)
       _ (println "BASE GRID:\n")
       _ (printgrid grid)
       ;_ (println)
       ;_ (println "WITH START POSITION (0) :\n")
       ;_ (printgrid (assoc grid start 0))
       ;_ (println "\nwidth:  " (g/width  grid)
       ;           "height: " (g/height grid)
       ;           "start " start "\n")
       ;_ (println (g/posis grid))
       _ (println "\n\n")
       filled (flood-fill grid start (fn [p] (= :ground (get grid p))))
       _ (printgrid (reduce #(assoc %1 %2 nil) grid filled))])
 )


(defn- creatures-with-level [creature-properties level]
  (filter #(= level (:creature/level %)) creature-properties))

(def ^:private creature->tile
  (memoize
   (fn [{:keys [property/id] :as prop}]
     (assert id)
     (let [image (prop->image prop)
           tile (->static-tiled-map-tile (:texture-region image))]
       (put! (m-props tile) "id" id)
       tile))))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [context spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (add-layer! tiled-map :name "creatures" :visible false)
        creature-properties (all-properties context :properties/creatures)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures-with-level creature-properties area-level)]
          (when (seq creatures)
            (set-tile! layer position (creature->tile (rand-nth creatures)))))))))

(defn generate-modules
  "The generated tiled-map needs to be disposed."
  [context {:keys [world/map-size
                   world/max-area-level
                   world/spawn-rate]}]
  (assert (<= max-area-level map-size))
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (printgrid grid)
        ;_ (println " - ")
        grid (reduce #(assoc %1 %2 :transition) grid (adjacent-wall-positions grid))
        ;_ (printgrid grid)
        ;_ (println " - ")
        _ (assert (or
                   (= #{:wall :ground :transition} (set (g/cells grid)))
                   (= #{:ground :transition} (set (g/cells grid))))
                  (str "(set (g/cells grid)): " (set (g/cells grid))))
        scale modules-scale
        scaled-grid (scale-grid grid scale)
        tiled-map (place-modules (load-map modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (g/posis grid))
                                 (filter #(= :transition (get grid %)) (g/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill scaled-grid start-position can-spawn?)
        ;_ (println "scaled grid with filled nil: '?' \n")
        ;_ (printgrid (reduce #(assoc %1 %2 nil) scaled-grid spawn-positions))
        ;_ (println "\n")
        {:keys [steps area-level-grid]} (->area-level-grid :grid grid
                                                           :start start
                                                           :max-level max-area-level
                                                           :walk-on #{:ground :transition})
        ;_ (printgrid area-level-grid)
        _ (assert (or
                   (= (set (concat [max-area-level] (range max-area-level)))
                      (set (g/cells area-level-grid)))
                   (= (set (concat [:wall max-area-level] (range max-area-level)))
                      (set (g/cells area-level-grid)))))
        scaled-area-level-grid (scale-grid area-level-grid scale)
        get-free-position-in-area-level (fn [area-level]
                                          (rand-nth
                                           (filter
                                            (fn [p]
                                              (and (= area-level (get scaled-area-level-grid p))
                                                   (#{:no-cell :undefined}
                                                    (property-value tiled-map :creatures p :id))))
                                            spawn-positions)))]
    (place-creatures! context spawn-rate tiled-map spawn-positions scaled-area-level-grid)
    {:tiled-map tiled-map
     :start-position (get-free-position-in-area-level 0)
     :area-level-grid scaled-area-level-grid}))

(defn uf-transition [position grid]
  (transition-idx-value position (= :transition (get grid position))))

(defn rand-0-3 []
  (get-rand-weighted-item {0 60 1 1 2 1 3 1}))

(defn rand-0-5 []
  (get-rand-weighted-item {0 30 1 1 2 1 3 1 4 1 5 1}))

; TODO zoomed out see that line of sight raycast goes x screens away
; only becuz of zoom?

; Level:
; * ground textures
; * wall textures
; * doodads ?
; * creatures w. that lvl
; (skills/items)
; * level-name (Goblin Lair , Halfling Village, Demon Kingdom)
; * spawn-rate
; * level-size
; * to finish lvl maybe find 3-4 signs to activate (takes some time) to open a portal
; every sign will increase spawn rate (maybe 0 at beginning -> can keep spawning)

; can use different algorithms(e.g. cave, module-gen-uf-terrain, room-gen? , differnt cave algorithm ...)

(defn- uf-place-creatures! [context spawn-rate tiled-map spawn-positions]
  (let [layer (add-layer! tiled-map :name "creatures" :visible false)
        creatures (all-properties context :properties/creatures)
        level (inc (rand-int 6))
        creatures (creatures-with-level creatures level)]
    ;(println "Level: " level)
    ;(println "Creatures with level: " (count creatures))
    (doseq [position spawn-positions
            :when (<= (rand) spawn-rate)]
      (set-tile! layer position (creature->tile (rand-nth creatures))))))

(def ^:private ->tm-tile
  (memoize
   (fn ->tm-tile [texture-region movement]
     {:pre [#{"all" "air" "none"} movement]}
     (let [tile (->static-tiled-map-tile texture-region)]
       (put! (m-props tile) "movement" movement)
       tile))))

(def ^:private sprite-size 48)

(defn- terrain-texture-region [ctx]
  (->texture-region (texture ctx "maps/uf_terrain.png")))

(defn- ->uf-tile [ctx & {:keys [sprite-x sprite-y movement]}]
  (->tm-tile (->texture-region (terrain-texture-region ctx)
                               [(* sprite-x sprite-size)
                                (* sprite-y sprite-size)
                                sprite-size
                                sprite-size])
             movement))

; TODO unused
(def ^:private ground-sprites [1 (range 5 11)])

(def ^:private uf-grounds
  (for [x [1 5]
        y (range 5 11)
        :when (not= [x y] [5 5])] ;wooden
    [x y]))

(def ^:private uf-walls
  (for [x [1]
        y [13,16,19,22,25,28]]
    [x y]))

(defn- ->ground-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-3)) :sprite-y y :movement "all"))

(defn- ->wall-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- ->transition-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- transition? [grid [x y]]
  (= :ground (get grid [x (dec y)])))

(def ^:private uf-caves-scale 4)

(defn uf-caves [ctx {:keys [world/map-size world/spawn-rate]}]
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (println "Start: " start)
        ;_ (printgrid grid)
        ;_ (println)
        scale uf-caves-scale
        grid (scalegrid grid scale)
        ;_ (printgrid grid)
        ;_ (println)
        start-position (mapv #(* % scale) start)
        grid (reduce #(assoc %1 %2 :transition) grid
                     (adjacent-wall-positions grid))
        _ (assert (or
                   (= #{:wall :ground :transition} (set (g/cells grid)))
                   (= #{:ground :transition}       (set (g/cells grid))))
                  (str "(set (g/cells grid)): " (set (g/cells grid))))
        ;_ (printgrid grid)
        ;_ (println)
        ground-idx (rand-nth uf-grounds)
        {wall-x 0 wall-y 1 :as wall-idx} (rand-nth uf-walls)
        transition-idx  [wall-x (inc wall-y)]
        position->tile (fn [position]
                         (case (get grid position)
                           :wall (->wall-tile ctx wall-idx)
                           :transition (if (transition? grid position)
                                         (->transition-tile ctx transition-idx)
                                         (->wall-tile ctx wall-idx))
                           :ground (->ground-tile ctx ground-idx)))
        tiled-map (wgt-grid->tiled-map grid position->tile)

        can-spawn? #(= "all" (movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill grid start-position can-spawn?)
        ]
    ; TODO don't spawn my faction vampire w. items ...
    ; TODO don't spawn creatures on start position
    ; (all check have HP/movement..../?? ?) (breaks potential field, targeting, ...)
    (uf-place-creatures! ctx spawn-rate tiled-map spawn-positions)
    {:tiled-map tiled-map
     :start-position start-position}))

(defcomponent :world/player-creature {:data :some #_[:one-to-one :properties/creatures]})

(defcomponent :world/map-size {:data :pos-int})
(defcomponent :world/max-area-level {:data :pos-int}) ; TODO <= map-size !?
(defcomponent :world/spawn-rate {:data :pos}) ; TODO <1 !

(defcomponent :world/tiled-map {:data :string})

(defcomponent :world/components {:data [:map []]})

(defcomponent :world/generator {:data [:enum [:world.generator/tiled-map
                                              :world.generator/modules
                                              :world.generator/uf-caves]]})

(defmulti generate (fn [_ctx world] (:world/generator world)))

(defmethod generate :world.generator/tiled-map [ctx world]
  {:tiled-map (load-map (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [ctx world]
  (generate-modules ctx world))

(defmethod generate :world.generator/uf-caves [ctx world]
  (uf-caves ctx world))

(extend-type clojure.world.Ctx
  WorldGen
  (->world [ctx world-id]
    (let [prop (build-property ctx world-id)]
      (assoc (generate ctx prop)
             :world/player-creature (:world/player-creature prop)))))

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (camera-set-position! camera
                        [(/ (width  tiled-map) 2)
                         (/ (height tiled-map) 2)])
  (set-zoom! camera
             (calculate-zoom camera
                             :left [0 0]
                             :top [0 (height tiled-map)]
                             :right [(width tiled-map) 0]
                             :bottom [0 0])))

(defn- current-data [ctx]
  (-> ctx
      current-screen
      (get 1)
      :sub-screen
      (get 1)))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- debug-infos ^String [ctx]
  (let [tile (->tile (world-mouse-position ctx))
        {:keys [tiled-map
                area-level-grid]} @(current-data ctx)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (world-mouse-position ctx)
                                 [module-width module-height])))
          (when area-level-grid
            (str "Creature id: " (property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (movement-property tiled-map tile) "\n"
               (apply vector (movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window [ctx]
  (let [label (->label "")
        window (->window {:title "Info" :rows [[label]]})]
    (add-actor! window (->actor {:act #(do
                                              (.setText label (debug-infos %))
                                              (.pack window))}))
    (set-position! window 0 (gui-viewport-height ctx))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (set-zoom! camera (max 0.1 (+ (zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [context camera]
  (when (.isKeyPressed gdx-input Input$Keys/SHIFT_LEFT)
    (adjust-zoom camera    zoom-speed))
  (when (.isKeyPressed gdx-input Input$Keys/MINUS)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (camera-set-position! camera
                                               (update (camera-position camera)
                                                       idx
                                                       #(f % camera-movement-speed))))]
    (if (.isKeyPressed gdx-input Input$Keys/LEFT)  (apply-position 0 -))
    (if (.isKeyPressed gdx-input Input$Keys/RIGHT) (apply-position 0 +))
    (if (.isKeyPressed gdx-input Input$Keys/UP)    (apply-position 1 +))
    (if (.isKeyPressed gdx-input Input$Keys/DOWN)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map [g ctx]
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data ctx)
        visible-tiles (visible-tiles (world-camera ctx))
        [x y] (->tile (world-mouse-position ctx))]
    (draw-rectangle g x y 1 1 Color/WHITE)
    (when start-position
      (draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (movement-property tiled-map [x y])]]
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                            0.08
                            Color/BLACK)
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                            0.05
                            (case movement-property
                              "all"   Color/GREEN
                              "air"   Color/ORANGE
                              "none"  Color/RED))))
    (when show-grid-lines
      (draw-grid g 0 0 (width  tiled-map) (height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate-screen-ctx [context properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (generate-modules context properties)
        {:keys [tiled-map start-position]} (->world context world-id)
        atom-data (current-data context)]
    (dispose (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (world-camera context) tiled-map)
    (.setVisible (get-layer tiled-map "creatures") true)
    context))

(defn ->generate-map-window [ctx level-id]
  (->window {:title "Properties"
                :cell-defaults {:pad 10}
                :rows [[(->label (with-out-str
                                     (clojure.pprint/pprint
                                      (build-property ctx level-id))))]
                       [(->text-button "Generate" #(try (generate-screen-ctx % (build-property % level-id))
                                                           (catch Throwable t
                                                             (error-window! % t)
                                                             (println t)
                                                             %)))]]
                :pack? true}))

(defcomponent ::sub-screen
  {:let current-data}
  ; TODO ?
  ;com.badlogic.gdx.utils.Disposable
  #_(dispose [_]
      (dispose (:tiled-map @current-data)))

  (screen-enter [_ ctx]
    (show-whole-map! (world-camera ctx) (:tiled-map @current-data)))

  (screen-exit [_ ctx]
    (reset-zoom! (world-camera ctx)))

  (screen-render [_ context]
    (render! context (:tiled-map @current-data) (constantly Color/WHITE))
    (render-world-view context #(render-on-map % context))
    (if (.isKeyJustPressed gdx-input Input$Keys/L)
      (swap! current-data update :show-grid-lines not))
    (if (.isKeyJustPressed gdx-input Input$Keys/M)
      (swap! current-data update :show-movement-properties not))
    (camera-controls context (world-camera context))
    (if (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
      (change-screen context :screens/main-menu)
      context)))

(derive :screens/map-editor :screens/stage)
(defcomponent :screens/map-editor
  (->mk [_ ctx]
    {:sub-screen [::sub-screen
                  (atom {:tiled-map (load-map modules-file)
                         :show-movement-properties false
                         :show-grid-lines false})]
     :stage (->stage ctx [(->generate-map-window ctx world-id)
                             (->info-window ctx)])}))

;; potential-fields

; Assumption: The map contains no not-allowed diagonal cells, diagonal wall cells where both
; adjacent cells are walls and blocked.
; (important for wavefront-expansion and field-following)
; * entities do not move to NADs (they remove them)
; * the potential field flows into diagonals, so they should be reachable too.
;
; TODO assert @ mapload no NAD's and @ potential field init & remove from
; potential-field-following the removal of NAD's.

(def ^:private pf-cache (atom nil))

(def factions-iterations {:good 15 :evil 5})

(defn- cell-blocked? [cell*]
  (blocked? cell* :z-order/ground))

; FIXME assert @ mapload no NAD's and @ potential field init & remove from
; potential-field-following the removal of NAD's.

; TODO remove max pot field movement player screen + 10 tiles as of screen size
; => is coupled to max-steps & also
; to friendly units follow player distance

; TODO remove cached get adj cells & use grid as atom not cells ?
; how to compare perfr ?

; TODO visualize steps, maybe I see something I missed

(comment
 (defrecord Foo [a b c])

 (let [^Foo foo (->Foo 1 2 3)]
   (time (dotimes [_ 10000000] (:a foo)))
   (time (dotimes [_ 10000000] (.a foo)))
   ; .a 7x faster ! => use for faction/distance & make record?
   ))

(comment
 ; Stepping through manually
 (clear-marked-cells! :good (get @faction->marked-cells :good))

 (defn- faction->tiles->entities-map* [entities]
   (into {}
         (for [[faction entities] (->> entities
                                       (filter   #(:entity/faction @%))
                                       (group-by #(:entity/faction @%)))]
           [faction
            (zipmap (map #(entity-tile @%) entities)
                    entities)])))

 (def max-iterations 1)

 (let [entities (map db/get-entity [140 110 91])
       tl->es (:good (faction->tiles->entities-map* entities))]
   tl->es
   (def last-marked-cells (generate-potential-field :good tl->es)))
 (println *1)
 (def marked *2)
 (step :good *1)
 )

(defn- diagonal-direction? [[x y]]
  (and (not (zero? (float x)))
       (not (zero? (float y)))))

(defn- diagonal-cells? [cell* other-cell*]
  (let [[x1 y1] (:position cell*)
        [x2 y2] (:position other-cell*)]
    (and (not= x1 x2)
         (not= y1 y2))))

(defrecord FieldData [distance entity])

(defn- add-field-data! [cell faction distance entity]
  (swap! cell assoc faction (->FieldData distance entity)))

(defn- remove-field-data! [cell faction]
  (swap! cell assoc faction nil)) ; don't dissoc - will lose the Cell record type

; TODO performance
; * cached-adjacent-non-blocked-cells ? -> no need for cell blocked check?
; * sorted-set-by ?
; * do not refresh the potential-fields EVERY frame, maybe very 100ms & check for exists? target if they died inbetween.
; (or teleported?)
(defn- step [grid faction last-marked-cells]
  (let [marked-cells (transient [])
        distance       #(nearest-entity-distance % faction)
        nearest-entity #(nearest-entity          % faction)
        marked? faction]
    ; sorting important because of diagonal-cell values, flow from lower dist first for correct distance
    (doseq [cell (sort-by #(distance @%) last-marked-cells)
            adjacent-cell (cached-adjacent-cells grid cell)
            :let [cell* @cell
                  adjacent-cell* @adjacent-cell]
            :when (not (or (cell-blocked? adjacent-cell*)
                           (marked? adjacent-cell*)))
            :let [distance-value (+ (float (distance cell*))
                                    (float (if (diagonal-cells? cell* adjacent-cell*)
                                             1.4 ; square root of 2 * 10
                                             1)))]]
      (add-field-data! adjacent-cell faction distance-value (nearest-entity cell*))
      (conj! marked-cells adjacent-cell))
    (persistent! marked-cells)))

(defn- generate-potential-field
  "returns the marked-cells"
  [grid faction tiles->entities max-iterations]
  (let [entity-cell-seq (for [[tile entity] tiles->entities] ; FIXME lazy seq
                          [entity (get grid tile)])
        marked (map second entity-cell-seq)]
    (doseq [[entity cell] entity-cell-seq]
      (add-field-data! cell faction 0 entity))
    (loop [marked-cells     marked
           new-marked-cells marked
           iterations 0]
      (if (= iterations max-iterations)
        marked-cells
        (let [new-marked (step grid faction new-marked-cells)]
          (recur (concat marked-cells new-marked) ; FIXME lazy seq
                 new-marked
                 (inc iterations)))))))

(defn- tiles->entities [entities faction]
  (let [entities (filter #(= (:entity/faction @%) faction)
                         entities)]
    (zipmap (map #(entity-tile @%) entities)
            entities)))

(defn- update-faction-potential-field [grid faction entities max-iterations]
  (let [tiles->entities (tiles->entities entities faction)
        last-state   [faction :tiles->entities]
        marked-cells [faction :marked-cells]]
    (when-not (= (get-in @pf-cache last-state) tiles->entities)
      (swap! pf-cache assoc-in last-state tiles->entities)
      (doseq [cell (get-in @pf-cache marked-cells)]
        (remove-field-data! cell faction))
      (swap! pf-cache assoc-in marked-cells (generate-potential-field
                                          grid
                                          faction
                                          tiles->entities
                                          max-iterations)))))

;; MOVEMENT AI

(defn- indexed ; from clojure.contrib.seq-utils (discontinued in 1.3)
  "Returns a lazy sequence of [index, item] pairs, where items come
 from 's' and indexes count up from zero.

 (indexed '(a b c d)) => ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))

(defn- utils-positions ; from clojure.contrib.seq-utils (discontinued in 1.3)
  "Returns a lazy sequence containing the positions at which pred
	 is true for items in coll."
  [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))


(let [order (g/get-8-neighbour-positions [0 0])]
  (def ^:private diagonal-check-indizes
    (into {} (for [[x y] (filter diagonal-direction? order)]
               [(first (utils-positions #(= % [x y]) order))
                (vec (utils-positions #(some #{%} [[x 0] [0 y]])
                                     order))]))))

(defn- is-not-allowed-diagonal? [at-idx adjacent-cells]
  (when-let [[a b] (get diagonal-check-indizes at-idx)]
    (and (nil? (adjacent-cells a))
         (nil? (adjacent-cells b)))))

(defn- remove-not-allowed-diagonals [adjacent-cells]
  (remove nil?
          (map-indexed
            (fn [idx cell]
              (when-not (or (nil? cell)
                            (is-not-allowed-diagonal? idx adjacent-cells))
                cell))
            adjacent-cells)))

; not using filter because nil cells considered @ remove-not-allowed-diagonals
; TODO only non-nil cells check
; TODO always called with cached-adjacent-cells ...
(defn- filter-viable-cells [entity adjacent-cells]
  (remove-not-allowed-diagonals
    (mapv #(when-not (or (cell-blocked? @%)
                         (occupied-by-other? @% entity))
             %)
          adjacent-cells)))

(defmacro ^:private when-seq [[aseq bind] & body]
  `(let [~aseq ~bind]
     (when (seq ~aseq)
       ~@body)))

(defn- get-min-dist-cell [distance-to cells]
  (when-seq [cells (filter distance-to cells)]
    (apply min-key distance-to cells)))

; rarely called -> no performance bottleneck
(defn- viable-cell? [grid distance-to own-dist entity cell]
  (when-let [best-cell (get-min-dist-cell
                        distance-to
                        (filter-viable-cells entity (cached-adjacent-cells grid cell)))]
    (when (< (float (distance-to best-cell)) (float own-dist))
      cell)))

(defn- find-next-cell
  "returns {:target-entity entity} or {:target-cell cell}. Cell can be nil."
  [grid entity own-cell]
  (let [faction (enemy-faction @entity)
        distance-to    #(nearest-entity-distance @% faction)
        nearest-entity #(nearest-entity          @% faction)
        own-dist (distance-to own-cell)
        adjacent-cells (cached-adjacent-cells grid own-cell)]
    (if (and own-dist (zero? (float own-dist)))
      {:target-entity (nearest-entity own-cell)}
      (if-let [adjacent-cell (first (filter #(and (distance-to %)
                                                  (zero? (float (distance-to %))))
                                            adjacent-cells))]
        {:target-entity (nearest-entity adjacent-cell)}
        {:target-cell (let [cells (filter-viable-cells entity adjacent-cells)
                            min-key-cell (get-min-dist-cell distance-to cells)]
                        (cond
                         (not min-key-cell)  ; red
                         own-cell

                         (not own-dist)
                         min-key-cell

                         (> (float (distance-to min-key-cell)) (float own-dist)) ; red
                         own-cell

                         (< (float (distance-to min-key-cell)) (float own-dist)) ; green
                         min-key-cell

                         (= (distance-to min-key-cell) own-dist) ; yellow
                         (or
                          (some #(viable-cell? grid distance-to own-dist entity %) cells)
                          own-cell)))}))))

(defn- inside-cell? [grid entity* cell]
  (let [cells (rectangle->cells grid entity*)]
    (and (= 1 (count cells))
         (= cell (first cells)))))

  ; TODO work with entity* !? occupied-by-other? works with entity not entity* ... not with ids ... hmmm
(defn- potential-field-follow-to-enemy* [world-grid entity] ; TODO pass faction here, one less dependency.
  (let [grid world-grid
        position (:position @entity)
        own-cell (get grid (->tile position))
        {:keys [target-entity target-cell]} (find-next-cell grid entity own-cell)]
    (cond
     target-entity
     (v-direction position (:position @target-entity))

     (nil? target-cell)
     nil

     :else
     (when-not (and (= target-cell own-cell)
                    (occupied-by-other? @own-cell entity)) ; prevent friction 2 move to center
       (when-not (inside-cell? grid @entity target-cell)
         (v-direction position (:middle @target-cell)))))))

(defn potential-fields-update! [{:keys [context/grid]} entities]
  (doseq [[faction max-iterations] factions-iterations]
    (update-faction-potential-field grid faction entities max-iterations)))

(extend-type clojure.world.Ctx
  Pathfinding
  (potential-fields-follow-to-enemy [{:keys [context/grid]} entity]
    (potential-field-follow-to-enemy* grid entity)))

;; DEBUG RENDER TODO not working in old map debug cdq.maps.render_

; -> render on-screen tile stuff
; -> I just use render-on-map and use tile coords
; -> I need the current viewed tiles x,y,w,h

#_(let [a 0.5]
  (color/defrgb transp-red 1 0 0 a)
  (color/defrgb transp-green 0 1 0 a)
  (color/defrgb transp-orange 1 0.34 0 a)
  (color/defrgb transp-yellow 1 1 0 a))

#_(def ^:private adjacent-cells-colors (atom nil))

#_(defn genmap
    "function is applied for every key to get value. use memoize instead?"
    [ks f]
    (zipmap ks (map f ks)))

#_(defn calculate-mouseover-body-colors [mouseoverbody]
  (when-let [body mouseoverbody]
    (let [occupied-cell (get (:context/grid context) (entity-tile @body))
          own-dist (distance-to occupied-cell)
          adj-cells (cached-adjacent-cells grid occupied-cell)
          potential-cells (filter distance-to
                                  (filter-viable-cells body adj-cells))
          adj-cells (remove nil? adj-cells)]
      (reset! adjacent-cells-colors
        (genmap adj-cells
          (fn [cell]
            (cond
              (not-any? #{cell} potential-cells)
              transp-red

              (not own-dist) ; die andre hat eine dist da sonst potential-cells rausgefiltert -> besser als jetzige cell.
              transp-green

              (< own-dist (distance-to cell))
              transp-red

              (= own-dist (distance-to cell))
              transp-yellow

              :else transp-green)))))))

#_(defn render-potential-field-following-mouseover-info
    [leftx topy xrect yrect cell mouseoverbody]
    (when-let [body mouseoverbody]
      (when-let [color (get @adjacent-cells-colors cell)]
        (shape-drawer/filled-rectangle leftx topy 1 1 color)))) ; FIXME scale ok for map rendering?

(defn- set-arr [arr cell* cell*->blocked?]
  (let [[x y] (:position cell*)]
    (aset arr x y (boolean (cell*->blocked? cell*)))))

(defcomponent :context/raycaster
  (->mk [[_ position->blocked?] {:keys [context/grid]}]
    (let [width  (g/width  grid)
          height (g/height grid)
          arr (make-array Boolean/TYPE width height)]
      (doseq [cell (g/cells grid)]
        (set-arr arr @cell position->blocked?))
      (map->ArrRayCaster {:arr arr
                          :width width
                          :height height}))))

; TO math.... // not tested
(defn- create-double-ray-endpositions
  "path-w in tiles."
  [[start-x start-y] [target-x target-y] path-w]
  {:pre [(< path-w 0.98)]} ; wieso 0.98??
  (let [path-w (+ path-w 0.02) ;etwas gr�sser damit z.b. projektil nicht an ecken anst�sst
        v (v-direction [start-x start-y]
                       [target-y target-y])
        [normal1 normal2] (v-get-normal-vectors v)
        normal1 (v-scale normal1 (/ path-w 2))
        normal2 (v-scale normal2 (/ path-w 2))
        start1  (v-add [start-x  start-y]  normal1)
        start2  (v-add [start-x  start-y]  normal2)
        target1 (v-add [target-x target-y] normal1)
        target2 (v-add [target-x target-y] normal2)]
    [start1,target1,start2,target2]))

(extend-type clojure.world.Ctx
  PRayCaster
  (ray-blocked? [{:keys [context/raycaster]} start target]
    (fast-ray-blocked? raycaster start target))

  (path-blocked? [{:keys [context/raycaster]} start target path-w]
    (let [[start1,target1,start2,target2] (create-double-ray-endpositions start target path-w)]
      (or
       (fast-ray-blocked? raycaster start1 target1)
       (fast-ray-blocked? raycaster start2 target2)))))

(defn- rectangle->tiles
  [{[x y] :left-bottom :keys [left-bottom width height]}]
  {:pre [left-bottom width height]}
  (let [x       (float x)
        y       (float y)
        width   (float width)
        height  (float height)
        l (int x)
        b (int y)
        r (int (+ x width))
        t (int (+ y height))]
    (set
     (if (or (> width 1) (> height 1))
       (for [x (range l (inc r))
             y (range b (inc t))]
         [x y])
       [[l b] [l t] [r b] [r t]]))))

(defn- set-cells! [grid entity]
  (let [cells (rectangle->cells grid @entity)]
    (assert (not-any? nil? cells))
    (swap! entity assoc ::touched-cells cells)
    (doseq [cell cells]
      (assert (not (get (:entities @cell) entity)))
      (swap! cell update :entities conj entity))))

(defn- remove-from-cells! [entity]
  (doseq [cell (::touched-cells @entity)]
    (assert (get (:entities @cell) entity))
    (swap! cell update :entities disj entity)))

; could use inside tiles only for >1 tile bodies (for example size 4.5 use 4x4 tiles for occupied)
; => only now there are no >1 tile entities anyway
(defn- rectangle->occupied-cells [grid {:keys [left-bottom width height] :as rectangle}]
  (if (or (> (float width) 1) (> (float height) 1))
    (rectangle->cells grid rectangle)
    [(get grid
          [(int (+ (float (left-bottom 0)) (/ (float width) 2)))
           (int (+ (float (left-bottom 1)) (/ (float height) 2)))])]))

(defn- set-occupied-cells! [grid entity]
  (let [cells (rectangle->occupied-cells grid @entity)]
    (doseq [cell cells]
      (assert (not (get (:occupied @cell) entity)))
      (swap! cell update :occupied conj entity))
    (swap! entity assoc ::occupied-cells cells)))

(defn- remove-from-occupied-cells! [entity]
  (doseq [cell (::occupied-cells @entity)]
    (assert (get (:occupied @cell) entity))
    (swap! cell update :occupied disj entity)))

; TODO LAZY SEQ @ g/get-8-neighbour-positions !!
; https://github.com/damn/g/blob/master/src/data/grid2d.clj#L126
(extend-type data.grid2d.Grid2D
  Grid
  (cached-adjacent-cells [grid cell]
    (if-let [result (:adjacent-cells @cell)]
      result
      (let [result (into [] (keep grid) (-> @cell :position g/get-8-neighbour-positions))]
        (swap! cell assoc :adjacent-cells result)
        result)))

  (rectangle->cells [grid rectangle]
    (into [] (keep grid) (rectangle->tiles rectangle)))

  (circle->cells [grid circle]
    (->> circle
         circle->outer-rectangle
         (rectangle->cells grid)))

  (circle->entities [grid circle]
    (->> (circle->cells grid circle)
         (map deref)
         cells->entities
         (filter #(shape-collides? circle @%)))))

(def ^:private this :context/grid)

(extend-type clojure.world.Ctx
  GridPointEntities
  (point->entities [ctx position]
    (when-let [cell (get (this ctx) (->tile position))]
      (filter #(point-in-rect? position @%)
              (:entities @cell)))))

(defn- grid-add-entity! [ctx entity]
  (let [grid (this ctx)]
    (set-cells! grid entity)
    (when (:collides? @entity)
      (set-occupied-cells! grid entity))))

(defn- grid-remove-entity! [ctx entity]
  (let [grid (this ctx)]
    (remove-from-cells! entity)
    (when (:collides? @entity)
      (remove-from-occupied-cells! entity))))

(defn- grid-entity-position-changed! [ctx entity]
  (let [grid (this ctx)] (remove-from-cells! entity)
    (set-cells! grid entity)
    (when (:collides? @entity)
      (remove-from-occupied-cells! entity)
      (set-occupied-cells! grid entity))))

(defrecord RCell [position
                 middle ; only used @ potential-field-follow-to-enemy -> can remove it.
                 adjacent-cells
                 movement
                 entities
                 occupied
                 good
                 evil]
  GridCell
  (blocked? [_ z-order]
    (case movement
      :none true ; wall
      :air (case z-order ; water/doodads
             :z-order/flying false
             :z-order/ground true)
      :all false)) ; ground/floor

  (blocks-vision? [_]
    (= movement :none))

  (occupied-by-other? [_ entity]
    (some #(not= % entity) occupied)) ; contains? faster?

  (nearest-entity [this faction]
    (-> this faction :entity))

  (nearest-entity-distance [this faction]
    (-> this faction :distance)))

(defn- create-cell [position movement]
  {:pre [(#{:none :air :all} movement)]}
  (map->RCell
   {:position position
    :middle (tile->middle position)
    :movement movement
    :entities #{}
    :occupied #{}}))

(defcomponent this
  (->mk [[_ [width height position->value]] _world]
    (g/create-grid width
                        height
                        #(atom (create-cell % (position->value %))))))

(def ^:private content-grid :context/content-grid)

(defn- content-grid-update-entity! [ctx entity]
  (let [{:keys [grid cell-w cell-h]} (content-grid ctx)
        {::keys [content-cell] :as entity*} @entity
        [x y] (:position entity*)
        new-cell (get grid [(int (/ x cell-w))
                            (int (/ y cell-h))])]
    (when-not (= content-cell new-cell)
      (swap! new-cell update :entities conj entity)
      (swap! entity assoc ::content-cell new-cell)
      (when content-cell
        (swap! content-cell update :entities disj entity)))))

(defn- content-grid-remove-entity! [_ entity]
  (-> @entity
      ::content-cell
      (swap! update :entities disj entity)))

(defn- active-entities* [ctx center-entity*]
  (let [{:keys [grid]} (content-grid ctx)]
    (->> (let [idx (-> center-entity*
                       ::content-cell
                       deref
                       :idx)]
           (cons idx (g/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(extend-type clojure.world.Ctx
  ActiveEntities
  (active-entities [ctx]
    (active-entities* ctx (player-entity* ctx))))

(defcomponent content-grid
  {:let [cell-w cell-h]}
  (->mk [_ {:keys [context/grid]}]
    {:grid (g/create-grid (inc (int (/ (g/width grid) cell-w))) ; inc because corners
                               (inc (int (/ (g/height grid) cell-h)))
                               (fn [idx]
                                 (atom {:idx idx,
                                        :entities #{}})))
     :cell-w cell-w
     :cell-h cell-h}))

(comment

 (defn get-all-entities-of-current-map [context]
   (mapcat (comp :entities deref)
           (g/cells (content-grid context))))

 (count
  (get-all-entities-of-current-map @app/state))

 )

(defcomponent :context/explored-tile-corners
  (->mk [_ {:keys [context/grid]}]
    (atom (g/create-grid (g/width grid)
                         (g/height grid)
                         (constantly false)))))

(def ^:private explored-tile-color (->color 0.5 0.5 0.5 1))

(def ^:private ^:dbg-flag see-all-tiles? false)

(comment
 (def ^:private count-rays? false)

 (def ray-positions (atom []))
 (def do-once (atom true))

 (count @ray-positions)
 2256
 (count (distinct @ray-positions))
 608
 (* 608 4)
 2432
 )

(defn- ->tile-color-setter [light-cache light-position raycaster explored-tile-corners]
  (fn tile-color-setter [_color x y]
    (let [position [(int x) (int y)]
          explored? (get @explored-tile-corners position) ; TODO needs int call ?
          base-color (if explored? explored-tile-color color-black)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (fast-ray-blocked? raycaster light-position position)]
                       (swap! light-cache assoc position blocked?)
                       blocked?)
                     cache-entry)]
      #_(when @do-once
          (swap! ray-positions conj position))
      (if blocked?
        (if see-all-tiles? color-white base-color)
        (do (when-not explored?
              (swap! explored-tile-corners assoc (->tile position) true))
            color-white)))))

(defn render-map [{:keys [context/tiled-map] :as ctx} light-position]
  (render! ctx
                 tiled-map
                 (->tile-color-setter (atom nil)
                                      light-position
                                      (:context/raycaster ctx)
                                      (:context/explored-tile-corners ctx)))
  #_(reset! do-once false))

(def ^:private ^:dbg-flag spawn-enemies? true)

(def ^:private player-components {:entity/state [:state/player :player-idle]
                                  :entity/faction :good
                                  :entity/player? true
                                  :entity/free-skill-points 3
                                  :entity/clickable {:type :clickable/player}
                                  :entity/click-distance-tiles 1.5})

(def ^:private npc-components {:entity/state [:state/npc :npc-sleeping]
                               :entity/faction :evil})

; player-creature needs mana & inventory
; till then hardcode :creatures/vampire
(defn- world->player-creature [{:keys [context/start-position]}
                               {:keys [world/player-creature]}]
  {:position start-position
   :creature-id :creatures/vampire #_(:property/id player-creature)
   :components player-components})

(defn- world->enemy-creatures [{:keys [context/tiled-map]}]
  (for [[position creature-id] (positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(defn spawn-creatures! [ctx tiled-level]
  (effect! ctx
           (for [creature (cons (world->player-creature ctx tiled-level)
                                (when spawn-enemies?
                                  (world->enemy-creatures ctx)))]
             [:tx/creature (update creature :position tile->middle)])))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
; done at module-gen? but not custom tiledmap?
(defn ->world-map [{:keys [tiled-map start-position]}] ; == one object make ?! like graphics?
  ; grep context/grid -> all dependent stuff?
  (create-into {:context/tiled-map tiled-map
                :context/start-position start-position}
               {:context/grid [(width  tiled-map)
                               (height tiled-map)
                               #(case (movement-property tiled-map %)
                                  "none" :none
                                  "air"  :air
                                  "all"  :all)]
                :context/raycaster blocks-vision?
                content-grid [16 16]
                :context/explored-tile-corners true}))

(defcomponent :tx/add-to-world
  (do! [[_ entity] ctx]
    (content-grid-update-entity! ctx entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid-add-entity! ctx entity)
    ctx))

(defcomponent :tx/remove-from-world
  (do! [[_ entity] ctx]
    (content-grid-remove-entity! ctx entity)
    (grid-remove-entity! ctx entity)
    ctx))

(defcomponent :tx/position-changed
  (do! [[_ entity] ctx]
    (content-grid-update-entity! ctx entity)
    (grid-entity-position-changed! ctx entity)
    ctx))
