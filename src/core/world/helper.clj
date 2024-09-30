(in-ns 'core.world)

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

