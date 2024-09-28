(ns core.world
  (:require [clojure.ctx :refer :all]
            [clojure.gdx :refer :all :exclude [visible?]]
            [clojure.gdx.tiled :refer :all]
            [clojure.string :as str]
            [data.grid2d :as g])
  (:load "world/helper"
         "world/modules"
         "world/caves"
         "world/areas"
         ))

; TODO this is part of world, not ctx
; as world-camera / world-unit-scale ...

(defn render!
  "Renders tiled-map using world-view at world-camera position and with world-unit-scale.
  Color-setter is a (fn [color x y]) which is called for every tile-corner to set the color.
  Can be used for lights & shadows.
  The map-renderers are created and cached internally.
  Renders only visible layers."
  [{g :context/graphics
    cached-map-renderer :context/tiled-map-renderer
    :as ctx}
   tiled-map
   color-setter]
  (render-tm! (cached-map-renderer g tiled-map)
              color-setter
              (world-camera ctx)
              tiled-map))

(defn ->tiled-map-renderer [{:keys [batch] :as g} tiled-map]
  (->orthogonal-tiled-map-renderer tiled-map
                                   (world-unit-scale g)
                                   batch))

(defcomponent :context/tiled-map-renderer
  {:data :some}
  (->mk [_ _ctx]
    (memoize ->tiled-map-renderer)))


; TODO generates 51,52. not max 50
; TODO can use different turn-ratio/depth/etc. params
; (printgrid (:grid (->cave-grid :size 800)))
(defn- ->cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (cave-gridgen (java.util.Random.) size size :wide)
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
  {:tiled-map (load-map (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [ctx world]
  (generate-modules ctx world))

(defmethod generate :world.generator/uf-caves [ctx world]
  (uf-caves ctx world))

(extend-type clojure.ctx.Context
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
  (when (key-pressed? :keys/shift-left)
    (adjust-zoom camera    zoom-speed))
  (when (key-pressed? :keys/minus)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (camera-set-position! camera
                                               (update (camera-position camera)
                                                       idx
                                                       #(f % camera-movement-speed))))]
    (if (key-pressed? :keys/left)  (apply-position 0 -))
    (if (key-pressed? :keys/right) (apply-position 0 +))
    (if (key-pressed? :keys/up)    (apply-position 1 +))
    (if (key-pressed? :keys/down)  (apply-position 1 -))))

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
    (draw-rectangle g x y 1 1 :white)
    (when start-position
      (draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (movement-property tiled-map [x y])]]
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)] 0.08 :black)
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                            0.05
                            (case movement-property
                              "all"   :green
                              "air"   :orange
                              "none"  :red))))
    (when show-grid-lines
      (draw-grid g 0 0 (width  tiled-map) (height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate-screen-ctx [context properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (generate-modules context properties)
        {:keys [tiled-map start-position]} (->world context world-id)
        atom-data (current-data context)]
    (dispose! (:tiled-map @atom-data))
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
  #_(dispose [_]
      (dispose! (:tiled-map @current-data)))

  (screen-enter [_ ctx]
    (show-whole-map! (world-camera ctx) (:tiled-map @current-data)))

  (screen-exit [_ ctx]
    (reset-zoom! (world-camera ctx)))

  (screen-render [_ context]
    (render! context (:tiled-map @current-data) (constantly white))
    (render-world-view context #(render-on-map % context))
    (if (key-just-pressed? :keys/l)
      (swap! current-data update :show-grid-lines not))
    (if (key-just-pressed? :keys/m)
      (swap! current-data update :show-movement-properties not))
    (camera-controls context (world-camera context))
    (if (key-just-pressed? :keys/escape)
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

(extend-type clojure.ctx.Context
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

(extend-type clojure.ctx.Context
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

(extend-type clojure.ctx.Context
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

(extend-type clojure.ctx.Context
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
          base-color (if explored? explored-tile-color black)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (fast-ray-blocked? raycaster light-position position)]
                       (swap! light-cache assoc position blocked?)
                       blocked?)
                     cache-entry)]
      #_(when @do-once
          (swap! ray-positions conj position))
      (if blocked?
        (if see-all-tiles? white base-color)
        (do (when-not explored?
              (swap! explored-tile-corners assoc (->tile position) true))
            white)))))

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
