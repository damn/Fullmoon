(ns core.world-gen
  (:require [clojure.string :as str]
            [data.grid2d :as g]
            [core.camera :as camera]
            [core.ctx :refer :all]
            [core.tiled :as tiled]
            [core.property :as property]
            [core.ui :as ui])
  (:import java.util.Random
           com.badlogic.gdx.Input$Keys
           com.badlogic.gdx.graphics.Color))

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
  (let [_ (assert (and (= (tiled/width modules-tiled-map)
                          (* number-modules-x (+ module-width module-offset-tiles)))
                       (= (tiled/height modules-tiled-map)
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
    (tiled/grid->tiled-map modules-tiled-map scaled-grid)))


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
     (let [image (property/->image prop)
           tile (tiled/->static-tiled-map-tile (:texture-region image))]
       (tiled/put! (tiled/m-props tile) "id" id)
       tile))))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [context spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (tiled/add-layer! tiled-map :name "creatures" :visible false)
        creature-properties (property/all-properties context :properties/creatures)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures-with-level creature-properties area-level)]
          (when (seq creatures)
            (tiled/set-tile! layer position (creature->tile (rand-nth creatures)))))))))

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
        tiled-map (place-modules (tiled/load-map modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (g/posis grid))
                                 (filter #(= :transition (get grid %)) (g/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (tiled/movement-property tiled-map %))
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
                                                    (tiled/property-value tiled-map :creatures p :id))))
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
  (let [layer (tiled/add-layer! tiled-map :name "creatures" :visible false)
        creatures (property/all-properties context :properties/creatures)
        level (inc (rand-int 6))
        creatures (creatures-with-level creatures level)]
    ;(println "Level: " level)
    ;(println "Creatures with level: " (count creatures))
    (doseq [position spawn-positions
            :when (<= (rand) spawn-rate)]
      (tiled/set-tile! layer position (creature->tile (rand-nth creatures))))))

(def ^:private ->tm-tile
  (memoize
   (fn ->tm-tile [texture-region movement]
     {:pre [#{"all" "air" "none"} movement]}
     (let [tile (tiled/->static-tiled-map-tile texture-region)]
       (tiled/put! (tiled/m-props tile) "movement" movement)
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
        tiled-map (tiled/wgt-grid->tiled-map grid position->tile)

        can-spawn? #(= "all" (tiled/movement-property tiled-map %))
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

(def-type :properties/worlds
  {:schema [:world/generator
            :world/player-creature
            [:world/tiled-map {:optional true}]
            [:world/map-size {:optional true}]
            [:world/max-area-level {:optional true}]
            [:world/spawn-rate {:optional true}]]
   :overview {:title "Worlds"
              :columns 10}})

(defmulti generate (fn [_ctx world] (:world/generator world)))

(defmethod generate :world.generator/tiled-map [ctx world]
  {:tiled-map (tiled/load-map (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [ctx world]
  (generate-modules ctx world))

(defmethod generate :world.generator/uf-caves [ctx world]
  (uf-caves ctx world))

(defn ->world [ctx world-id]
  (let [prop (build-property ctx world-id)]
    (assoc (generate ctx prop)
           :world/player-creature (:world/player-creature prop))))

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (camera/set-position! camera
                        [(/ (tiled/width  tiled-map) 2)
                         (/ (tiled/height tiled-map) 2)])
  (camera/set-zoom! camera
                    (camera/calculate-zoom camera
                                           :left [0 0]
                                           :top [0 (tiled/height tiled-map)]
                                           :right [(tiled/width tiled-map) 0]
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
            (str "Creature id: " (tiled/property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (tiled/movement-property tiled-map tile) "\n"
               (apply vector (tiled/movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window [ctx]
  (let [label (ui/->label "")
        window (ui/->window {:title "Info" :rows [[label]]})]
    (ui/add-actor! window (ui/->actor {:act #(do
                                              (.setText label (debug-infos %))
                                              (.pack window))}))
    (ui/set-position! window 0 (gui-viewport-height ctx))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

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
                         (camera/set-position! camera
                                               (update (camera/position camera)
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
        visible-tiles (camera/visible-tiles (world-camera ctx))
        [x y] (->tile (world-mouse-position ctx))]
    (draw-rectangle g x y 1 1 Color/WHITE)
    (when start-position
      (draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (tiled/movement-property tiled-map [x y])]]
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
      (draw-grid g 0 0 (tiled/width  tiled-map) (tiled/height tiled-map) 1 1 [1 1 1 0.5]))))

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
    (.setVisible (tiled/get-layer tiled-map "creatures") true)
    context))

(defn ->generate-map-window [ctx level-id]
  (ui/->window {:title "Properties"
                :cell-defaults {:pad 10}
                :rows [[(ui/->label (with-out-str
                                     (clojure.pprint/pprint
                                      (build-property ctx level-id))))]
                       [(ui/->text-button "Generate" #(try (generate-screen-ctx % (build-property % level-id))
                                                           (catch Throwable t
                                                             (ui/error-window! % t)
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
    (camera/reset-zoom! (world-camera ctx)))

  (screen-render [_ context]
    (tiled/render! context (:tiled-map @current-data) (constantly Color/WHITE))
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
                  (atom {:tiled-map (tiled/load-map modules-file)
                         :show-movement-properties false
                         :show-grid-lines false})]
     :stage (ui/->stage ctx [(->generate-map-window ctx world-id)
                             (->info-window ctx)])}))
