(ns level.generate
  (:require [component.core :refer [defc]]
            [component.db :as db]
            [component.property :as property]
            [data.grid2d :as g2d]
            [gdx.graphics :as g]
            [gdx.tiled :as t]
            [gdx.rand :refer [get-rand-weighted-item]]
            [utils.core :refer [->tile assoc-ks]]
            [level.area-level-grid :as area-level-grid]
            [level.caves :as caves]
            [level.grid :refer [scale-grid scalegrid printgrid transition-idx-value fix-not-allowed-diagonals cave-grid adjacent-wall-positions flood-fill]]
            [level.modules :as modules :refer [modules-scale module-width module-height]]
            [level.tiled :refer [movement-properties movement-property wgt-grid->tiled-map]]))

(defn- creatures-with-level [creature-properties level]
  (filter #(= level (:creature/level %)) creature-properties))

(def ^:private creature->tile
  (memoize
   (fn [{:keys [property/id] :as prop}]
     (assert id)
     (let [image (property/->image prop)
           tile (t/->static-tiled-map-tile (:texture-region image))]
       (t/put! (t/m-props tile) "id" id)
       tile))))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (t/add-layer! tiled-map :name "creatures" :visible false)
        creature-properties (db/all :properties/creatures)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures-with-level creature-properties area-level)]
          (when (seq creatures)
            (t/set-tile! layer position (creature->tile (rand-nth creatures)))))))))

(def modules-file "maps/modules.tmx")

(defn generate-modules
  "The generated tiled-map needs to be disposed."
  [{:keys [world/map-size
           world/max-area-level
           world/spawn-rate]}]
  (assert (<= max-area-level map-size))
  (let [{:keys [start grid]} (cave-grid :size map-size)
        ;_ (printgrid grid)
        ;_ (println " - ")
        grid (reduce #(assoc %1 %2 :transition) grid (adjacent-wall-positions grid))
        ;_ (printgrid grid)
        ;_ (println " - ")
        _ (assert (or
                   (= #{:wall :ground :transition} (set (g2d/cells grid)))
                   (= #{:ground :transition} (set (g2d/cells grid))))
                  (str "(set (g2d/cells grid)): " (set (g2d/cells grid))))
        scale modules-scale
        scaled-grid (scale-grid grid scale)
        tiled-map (modules/place (t/load-map modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (g2d/posis grid))
                                 (filter #(= :transition (get grid %)) (g2d/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill scaled-grid start-position can-spawn?)
        ;_ (println "scaled grid with filled nil: '?' \n")
        ;_ (printgrid (reduce #(assoc %1 %2 nil) scaled-grid spawn-positions))
        ;_ (println "\n")
        {:keys [steps area-level-grid]} (area-level-grid/create
                                         :grid grid
                                         :start start
                                         :max-level max-area-level
                                         :walk-on #{:ground :transition})
        ;_ (printgrid area-level-grid)
        _ (assert (or
                   (= (set (concat [max-area-level] (range max-area-level)))
                      (set (g2d/cells area-level-grid)))
                   (= (set (concat [:wall max-area-level] (range max-area-level)))
                      (set (g2d/cells area-level-grid)))))
        scaled-area-level-grid (scale-grid area-level-grid scale)
        get-free-position-in-area-level (fn [area-level]
                                          (rand-nth
                                           (filter
                                            (fn [p]
                                              (and (= area-level (get scaled-area-level-grid p))
                                                   (#{:no-cell :undefined}
                                                    (t/property-value tiled-map :creatures p :id))))
                                            spawn-positions)))]
    (place-creatures! spawn-rate tiled-map spawn-positions scaled-area-level-grid)
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

(defn- uf-place-creatures! [spawn-rate tiled-map spawn-positions]
  (let [layer (t/add-layer! tiled-map :name "creatures" :visible false)
        creatures (db/all :properties/creatures)
        level (inc (rand-int 6))
        creatures (creatures-with-level creatures level)]
    ;(println "Level: " level)
    ;(println "Creatures with level: " (count creatures))
    (doseq [position spawn-positions
            :when (<= (rand) spawn-rate)]
      (t/set-tile! layer position (creature->tile (rand-nth creatures))))))

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

(defn uf-caves [{:keys [world/map-size world/spawn-rate]}]
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

(defc :world/player-creature {:schema :some #_[:s/one-to-one :properties/creatures]})

(defc :world/map-size {:schema pos-int?})
(defc :world/max-area-level {:schema pos-int?}) ; TODO <= map-size !?
(defc :world/spawn-rate {:schema pos?}) ; TODO <1 !

(defc :world/tiled-map {:schema :string})

(defc :world/components {:schema [:s/map []]})

(defc :world/generator {:schema [:enum
                               :world.generator/tiled-map
                               :world.generator/modules
                               :world.generator/uf-caves]})

(property/def :properties/worlds
  {:schema [:world/generator
            :world/player-creature
            [:world/tiled-map {:optional true}]
            [:world/map-size {:optional true}]
            [:world/max-area-level {:optional true}]
            [:world/spawn-rate {:optional true}]]
   :overview {:title "Worlds"
              :columns 10}})

(defmulti generate (fn [world] (:world/generator world)))

(defmethod generate :world.generator/tiled-map [world]
  {:tiled-map (t/load-map (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [world]
  (generate-modules world))

(defmethod generate :world.generator/uf-caves [world]
  (uf-caves world))

(defn generate-level [world-id]
  (let [prop (db/get world-id)]
    (assoc (generate prop) :world/player-creature (:world/player-creature prop))))
