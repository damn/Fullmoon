(ns world.generate
  (:require [clojure.gdx.graphics :as g :refer [white black]]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.input :refer [key-pressed? key-just-pressed?]]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :as stage-screen]
            [clojure.gdx.screen :as screen]
            [clojure.gdx.utils :refer [dispose!]]
            [clojure.gdx.tiled :as t]
            [clojure.gdx.rand :refer [get-rand-weighted-item]]
            [clojure.string :as str]
            [core.component :refer [defc]]
            [core.db :as db]
            [core.property :as property]
            [core.widgets.error :refer [error-window!]]
            [data.grid2d :as g2d]
            [utils.core :refer [->tile assoc-ks]]
            [world.generate.area-level-grid :as area-level-grid]
            [world.generate.caves :as caves]
            [world.generate.modules :as modules :refer [modules-scale
                                                        module-width
                                                        module-height]]))

; TODO generates 51,52. not max 50
; TODO can use different turn-ratio/depth/etc. params
; (printgrid (:grid (->cave-grid :size 800)))
(defn- ->cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (caves/generate (java.util.Random.) size size :wide)
        grid (t/fix-not-allowed-diagonals grid)]
    (assert (= #{:wall :ground} (set (g2d/cells grid))))
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
                             (g2d/get-8-neighbour-positions p))))
          (g2d/posis grid)))

(defn- flood-fill [grid start walk-on-position?]
  (loop [next-positions [start]
         filled []
         grid grid]
    (if (seq next-positions)
      (recur (filter #(and (get grid %)
                           (walk-on-position? %))
                     (distinct
                      (mapcat g2d/get-8-neighbour-positions
                              next-positions)))
             (concat filled next-positions)
             (assoc-ks grid next-positions nil))
      filled)))

(comment
 (let [{:keys [start grid]} (t/->cave-grid :size 15)
       _ (println "BASE GRID:\n")
       _ (printgrid grid)
       ;_ (println)
       ;_ (println "WITH START POSITION (0) :\n")
       ;_ (printgrid (assoc grid start 0))
       ;_ (println "\nwidth:  " (g/width  grid)
       ;           "height: " (g/height grid)
       ;           "start " start "\n")
       ;_ (println (g2d/posis grid))
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
     (let [image (property/image prop)
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
  (let [{:keys [start grid]} (->cave-grid :size map-size)
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
        scaled-grid (t/scale-grid grid scale)
        tiled-map (modules/place (t/load-map modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (g2d/posis grid))
                                 (filter #(= :transition (get grid %)) (g2d/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (t/movement-property tiled-map %))
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
        scaled-area-level-grid (t/scale-grid area-level-grid scale)
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
  (t/transition-idx-value position (= :transition (get grid position))))

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
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (println "Start: " start)
        ;_ (printgrid grid)
        ;_ (println)
        scale uf-caves-scale
        grid (t/scalegrid grid scale)
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
        tiled-map (t/wgt-grid->tiled-map grid position->tile)
        can-spawn? #(= "all" (t/movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill grid start-position can-spawn?)
        ]
    ; TODO don't spawn my faction vampire w. items ...
    ; TODO don't spawn creatures on start position
    ; (all check have HP/movement..../?? ?) (breaks potential field, targeting, ...)
    (uf-place-creatures! spawn-rate tiled-map spawn-positions)
    {:tiled-map tiled-map
     :start-position start-position}))

(db/def-attr :world/player-creature :some #_[:one-to-one :properties/creatures])

(db/def-attr :world/map-size :pos-int)
(db/def-attr :world/max-area-level :pos-int) ; TODO <= map-size !?
(db/def-attr :world/spawn-rate :pos) ; TODO <1 !
(db/def-attr :world/tiled-map :string)
(db/def-attr :world/components [:map []])
(db/def-attr :world/generator [:enum
                               :world.generator/tiled-map
                               :world.generator/modules
                               :world.generator/uf-caves])

(db/def-property :properties/worlds
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

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (🎥/set-position! camera
                    [(/ (t/width  tiled-map) 2)
                     (/ (t/height tiled-map) 2)])
  (🎥/set-zoom! camera
                (🎥/calculate-zoom camera
                                   :left [0 0]
                                   :top [0 (t/height tiled-map)]
                                   :right [(t/width tiled-map) 0]
                                   :bottom [0 0])))

; use var !
(defn- current-data []
  (-> (screen/current)
      :sub-screen
      :current-data))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- map-infos ^String []
  (let [tile (->tile (g/world-mouse-position))
        {:keys [tiled-map
                area-level-grid]} @(current-data)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (g/world-mouse-position)
                                 [module-width module-height])))
          (when area-level-grid
            (str "Creature id: " (t/property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (t/movement-property tiled-map tile) "\n"
               (apply vector (t/movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window []
  (let [label (ui/label "")
        window (ui/window {:title "Info" :rows [[label]]})]
    (ui/add-actor! window (ui/actor {:act #(do
                                            (.setText label (map-infos))
                                            (.pack window))}))
    (a/set-position! window 0 (g/gui-viewport-height))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (🎥/set-zoom! camera (max 0.1 (+ (🎥/zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [camera]
  (when (key-pressed? :keys/shift-left)
    (adjust-zoom camera    zoom-speed))
  (when (key-pressed? :keys/minus)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (🎥/set-position! camera
                                           (update (🎥/position camera)
                                                   idx
                                                   #(f % camera-movement-speed))))]
    (if (key-pressed? :keys/left)  (apply-position 0 -))
    (if (key-pressed? :keys/right) (apply-position 0 +))
    (if (key-pressed? :keys/up)    (apply-position 1 +))
    (if (key-pressed? :keys/down)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map []
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data)
        visible-tiles (🎥/visible-tiles (g/world-camera))
        [x y] (mapv int (g/world-mouse-position))]
    (g/draw-rectangle x y 1 1 :white)
    (when start-position
      (g/draw-filled-rectangle (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (t/movement-property tiled-map [x y])]]
        (g/draw-filled-circle [(+ x 0.5) (+ y 0.5)] 0.08 :black)
        (g/draw-filled-circle [(+ x 0.5) (+ y 0.5)]
                              0.05
                              (case movement-property
                                "all"   :green
                                "air"   :orange
                                "none"  :red))))
    (when show-grid-lines
      (g/draw-grid 0 0 (t/width  tiled-map) (t/height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate-screen-ctx [properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (generate-modules context properties)
        {:keys [tiled-map start-position]} (generate-level world-id)
        atom-data (current-data)]
    (dispose! (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (g/world-camera) tiled-map)
    (.setVisible (t/get-layer tiled-map "creatures") true)))

(defn ->generate-map-window [level-id]
  (ui/window {:title "Properties"
              :cell-defaults {:pad 10}
              :rows [[(ui/label (with-out-str
                                (clojure.pprint/pprint
                                 (db/get level-id))))]
                     [(ui/text-button "Generate" #(try (generate-screen-ctx (db/get level-id))
                                                       (catch Throwable t
                                                         (error-window! t)
                                                         (println t))))]]
              :pack? true}))

(defrecord MapEditorScreen [current-data]
  screen/Screen
  (screen/enter! [_]
    (show-whole-map! (g/world-camera) (:tiled-map @current-data)))

  (screen/exit! [_]
    (🎥/reset-zoom! (g/world-camera)))

  (screen/render! [_]
    (g/draw-tiled-map (:tiled-map @current-data) (constantly white))
    (g/render-world-view! render-on-map)
    (if (key-just-pressed? :keys/l)
      (swap! current-data update :show-grid-lines not))
    (if (key-just-pressed? :keys/m)
      (swap! current-data update :show-movement-properties not))
    (camera-controls (g/world-camera))
    (when (key-just-pressed? :keys/escape)
      (screen/change! :screens/main-menu)))

  (dispose! [_]
    (dispose! (:tiled-map @current-data))))

(defn map-editor-screen []
  [:screens/map-editor
   (stage-screen/create :actors [(->generate-map-window world-id)
                                 (->info-window)]
                        :screen (->MapEditorScreen (atom {:tiled-map (t/load-map modules-file)
                                                          :show-movement-properties false
                                                          :show-grid-lines false})))])
