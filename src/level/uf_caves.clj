(ns level.uf-caves
  (:require [data.grid2d :as g2d]
            [component.db :as db]
            [gdx.graphics :as g]
            [gdx.tiled :as t]
            [gdx.rand :refer [get-rand-weighted-item]]
            [level.creatures :as creatures]
            [level.grid :refer [scalegrid printgrid transition-idx-value cave-grid adjacent-wall-positions flood-fill]]
            [level.tiled :refer [movement-property wgt-grid->tiled-map]]))

(defn- uf-transition [position grid]
  (transition-idx-value position (= :transition (get grid position))))

(defn- rand-0-3 []
  (get-rand-weighted-item {0 60 1 1 2 1 3 1}))

(defn- rand-0-5 []
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

(defn- uf-place-creatures! [spawn-rate tiled-map spawn-positions]
  (let [layer (t/add-layer! tiled-map :name "creatures" :visible false)
        creatures (db/all :properties/creatures)
        level (inc (rand-int 6))
        creatures (creatures/with-level creatures level)]
    ;(println "Level: " level)
    ;(println "Creatures with level: " (count creatures))
    (doseq [position spawn-positions
            :when (<= (rand) spawn-rate)]
      (t/set-tile! layer position (creatures/tile (rand-nth creatures))))))

(def ^:private ->tm-tile
  (memoize
   (fn ->tm-tile [texture-region movement]
     {:pre [#{"all" "air" "none"} movement]}
     (let [tile (t/->static-tiled-map-tile texture-region)]
       (t/put! (t/m-props tile) "movement" movement)
       tile))))

(def ^:private sprite-size 48)

(defn- terrain-texture-region []
  (g/->texture-region "maps/uf_terrain.png"))

(defn- ->uf-tile [& {:keys [sprite-x sprite-y movement]}]
  (->tm-tile (g/->texture-region (terrain-texture-region)
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

(defn- ->ground-tile [[x y]]
  (->uf-tile :sprite-x (+ x (rand-0-3)) :sprite-y y :movement "all"))

(defn- ->wall-tile [[x y]]
  (->uf-tile :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- ->transition-tile [[x y]]
  (->uf-tile :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- transition? [grid [x y]]
  (= :ground (get grid [x (dec y)])))

(def ^:private uf-caves-scale 4)

(defn create [{:keys [world/map-size world/spawn-rate]}]
  (let [{:keys [start grid]} (cave-grid :size map-size)
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
                   (= #{:wall :ground :transition} (set (g2d/cells grid)))
                   (= #{:ground :transition}       (set (g2d/cells grid))))
                  (str "(set (g2d/cells grid)): " (set (g2d/cells grid))))
        ;_ (printgrid grid)
        ;_ (println)
        ground-idx (rand-nth uf-grounds)
        {wall-x 0 wall-y 1 :as wall-idx} (rand-nth uf-walls)
        transition-idx  [wall-x (inc wall-y)]
        position->tile (fn [position]
                         (case (get grid position)
                           :wall (->wall-tile wall-idx)
                           :transition (if (transition? grid position)
                                         (->transition-tile transition-idx)
                                         (->wall-tile wall-idx))
                           :ground (->ground-tile ground-idx)))
        tiled-map (wgt-grid->tiled-map grid position->tile)
        can-spawn? #(= "all" (movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill grid start-position can-spawn?)
        ]
    ; TODO don't spawn my faction vampire w. items ...
    ; TODO don't spawn creatures on start position
    ; (all check have HP/movement..../?? ?) (breaks potential field, targeting, ...)
    (uf-place-creatures! spawn-rate tiled-map spawn-positions)
    {:tiled-map tiled-map
     :start-position start-position}))
