(ns mapgen.module-gen
  (:require [utils.core :refer [assoc-ks]]
            [data.grid2d :as grid]
            [core.maps.tiled :as tiled]
            [core.context :as ctx :refer [->tiled-map]]
            [core.context :refer [all-properties]]
            [mapgen.utils :refer [printgrid scale-grid]]
            [mapgen.tiled-utils :refer [->static-tiled-map-tile set-tile! put! add-layer! grid->tiled-map]]
            [mapgen.transitions :as transitions]
            [mapgen.cave-gen :as cave-gen]
            [mapgen.nad :as nad])
  (:import java.util.Random))

; TODO generates 51,52. not max 50
; TODO can use different turn-ratio/depth/etc. params
(defn- ->cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (cave-gen/cave-gridgen (Random.) size size :wide)
        grid (nad/fix-not-allowed-diagonals grid)]
    (assert (= #{:wall :ground} (set (grid/cells grid))))
    {:start start
     :grid grid}))

; (printgrid (:grid (->cave-grid :size 800)))

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
                             (grid/get-8-neighbour-positions p))))
          (grid/posis grid)))

(def modules-file "maps/modules.tmx")
(def module-width  32)
(def module-height 20)
(def ^:private number-modules-x 8)
(def ^:private number-modules-y 4)
(def ^:private module-offset-tiles 1)
(def ^:private transition-modules-row-width 4)
(def ^:private transition-modules-row-height 4)
(def ^:private transition-modules-offset-x 4)
(def ^:private floor-modules-row-width 4)
(def ^:private floor-modules-row-height 4)

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

(def ^:private floor-idxvalue 0)
(def ^:private scale [module-width module-height])

(defn- place-module [scaled-grid
                     unscaled-position
                     & {:keys [transition?
                               transition-neighbor?]}]
  (let [idxvalue (if transition?
                   (transitions/index-value unscaled-position
                                            transition-neighbor?)
                   floor-idxvalue)
        tiled-map-positions (module-index->tiled-map-positions
                             (if transition?
                               (transition-idxvalue->module-index idxvalue)
                               (floor->module-index)))
        offsets (for [x (range module-width)
                      y (range module-height)]
                  [x y])
        offset->tiled-map-position (zipmap offsets tiled-map-positions)
        scaled-position (mapv * unscaled-position scale)]
    (reduce (fn [grid offset]
              (assoc grid
                     (mapv + scaled-position offset)
                     (offset->tiled-map-position offset)))
            scaled-grid
            offsets)))

(defn- place-modules [modules-tiled-map
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
    (grid->tiled-map modules-tiled-map scaled-grid)))

(defn- flood-fill [grid start walk-on-position?]
  (loop [next-positions [start]
         filled []
         grid grid]
    (if (seq next-positions)
      (recur (filter #(and (get grid %)
                           (walk-on-position? %))
                     (distinct
                      (mapcat grid/get-8-neighbour-positions
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
       ;_ (println "\nwidth:  " (grid/width  grid)
       ;           "height: " (grid/height grid)
       ;           "start " start "\n")
       ;_ (println (grid/posis grid))
       _ (println "\n\n")
       filled (flood-fill grid start (fn [p] (= :ground (get grid p))))
       _ (printgrid (reduce #(assoc %1 %2 nil) grid filled))])
 )

; can adjust:
; * split percentage , for higher level areas may scale faster (need to be more careful)
; * not 4 neighbors but just 1 tile randomwalk -> possible to have lvl 9 area next to lvl 1 ?
; * adds metagame to the game , avoid/or fight higher level areas, which areas to go next , etc...
; -> up to the player not step by step level increase like D2
; can not only take first of added-p but multiples also
; can make parameter how fast it scales
; area-level-grid works better with more wide grids
; if the cave is very straight then it is just a continous progression and area-level-grid is useless
(defn- ->area-level-grid
  "Expands from start position by adding one random adjacent neighbor.
  Each random walk is a step and is assigned a level as of max-level.
  (Levels are scaled, for example grid has 100 ground cells, so steps would be 0 to 100(99?)
  and max-level will smooth it out over 0 to max-level.
  The point of this is to randomize the levels so player does not have a smooth progression
  but can encounter higher level areas randomly around but there is always a path which goes from
  level 0 to max-level, so the player has to decide which areas to do in which order."
  [& {:keys [grid start max-level walk-on]}]
  (let [maxcount (->> grid
                      grid/cells
                      (filter walk-on)
                      count)
        ; -> assume all :ground cells can be reached from start
        ; later check steps count == maxcount assert
        level-step (/ maxcount max-level)
        step->level #(int (Math/ceil (/ % level-step)))
        walkable-neighbours (fn [grid position]
                              (filter #(walk-on (get grid %))
                                      (grid/get-4-neighbour-positions position)))]
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

(defn- creatures-with-level [creature-properties level]
  (filter #(= level (:creature/level %)) creature-properties))

(def ^:private creature->tile
  (memoize
   (fn [{:keys [property/id property/image]}]
     (assert (and id image))
     (let [tile (->static-tiled-map-tile (:texture-region image))]
       (put! (tiled/properties tile) "id" id)
       tile))))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [context spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (add-layer! tiled-map :name "creatures" :visible true)
        creature-properties (all-properties context :properties/creature)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures-with-level creature-properties area-level)]
          (when (seq creatures)
            (set-tile! layer position (creature->tile (rand-nth creatures)))))))))

(defn- place-princess! [context tiled-map position princess]
  (set-tile! (tiled/get-layer tiled-map "creatures")
             position
             (creature->tile (core.context/get-property context princess))))

(defn generate
  "The generated tiled-map needs to be disposed."
  [context {:keys [world/map-size
                   world/max-area-level
                   world/spawn-rate
                   world/princess]}]
  (assert (<= max-area-level map-size))
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (printgrid grid)
        ;_ (println " - ")
        grid (reduce #(assoc %1 %2 :transition) grid (adjacent-wall-positions grid))
        ;_ (printgrid grid)
        ;_ (println " - ")
        _ (assert (or
                   (= #{:wall :ground :transition} (set (grid/cells grid)))
                   (= #{:ground :transition} (set (grid/cells grid))))
                  (str "(set (grid/cells grid)): " (set (grid/cells grid))))
        scaled-grid (scale-grid grid scale)
        tiled-map (place-modules (->tiled-map context modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (grid/posis grid))
                                 (filter #(= :transition (get grid %)) (grid/posis grid)))
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
                      (set (grid/cells area-level-grid)))
                   (= (set (concat [:wall max-area-level] (range max-area-level)))
                      (set (grid/cells area-level-grid)))))
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
    (place-princess! context tiled-map (get-free-position-in-area-level max-area-level) princess)
    {:tiled-map tiled-map
     :start-position (get-free-position-in-area-level 0)
     :area-level-grid scaled-area-level-grid}))

(require '[gdx.graphics.g2d :refer [->texture-region]])

(defn uf-transition [position grid]
  (transitions/index-value position (= :transition (get grid position))))

(defn rand-0-3 []
  (utils.random/get-rand-weighted-item
   {0 60 1 1 2 1 3 1}))

(defn rand-0-5 []
  (utils.random/get-rand-weighted-item
   {0 30 1 1 2 1 3 1 4 1 5 1}))

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
  (let [layer (add-layer! tiled-map :name "creatures" :visible true)
        creatures (all-properties context :properties/creature)
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
       (put! (tiled/properties tile) "movement" movement)
       tile))))

(def ^:private sprite-size 48)

(defn- terrain-texture-region [ctx]
  (->texture-region (ctx/cached-texture ctx "maps/uf_terrain.png")))

(defn- ->uf-tile [ctx & {:keys [sprite-x sprite-y movement]}]
  (->tm-tile (->texture-region (terrain-texture-region ctx)
                               [(* sprite-x sprite-size)
                                (* sprite-y sprite-size)
                                sprite-size
                                sprite-size])
             movement))

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
  (let [map-size 30
        spawn-rate 0.02
        {:keys [start grid]} (->cave-grid :size map-size)
        ;_ (println "Start: " start)
        ;_ (printgrid grid)
        ;_ (println)
        scale uf-caves-scale
        grid (mapgen.utils/scalegrid grid scale)
        ;_ (printgrid grid)
        ;_ (println)
        start-position (mapv #(* % scale) start)
        grid (reduce #(assoc %1 %2 :transition) grid
                     (adjacent-wall-positions grid))
        _ (assert (or
                   (= #{:wall :ground :transition} (set (grid/cells grid)))
                   (= #{:ground :transition}       (set (grid/cells grid))))
                  (str "(set (grid/cells grid)): " (set (grid/cells grid))))
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
        tiled-map (mapgen.tiled-utils/wgt-grid->tiled-map grid position->tile)

        can-spawn? #(= "all" (tiled/movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill grid start-position can-spawn?)
        ]
    ; TODO don't spawn my faction vampire w. items ...
    ; TODO don't spawn princess with clickable
    ; TODO don't spawn creatures on start position
    ; (all check have HP/movement..../?? ?) (breaks potential field, targeting, ...)
    (uf-place-creatures! ctx spawn-rate tiled-map spawn-positions)
    {:tiled-map tiled-map
     :start-position start-position}))
