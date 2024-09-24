(ns core.world
  (:require [data.grid2d :as grid2d]
            [core.utils.core :as utils]
            [core.ctx :refer :all]
            [core.tiled :as tiled]
            [core.property :as property]
            [core.camera :as camera]
            [core.ui :as ui]
            [core.entity :as entity]
            [core.entity-state :refer [draw-item-on-cursor]]
            [core.world.gen.gen :as level-generator]
            [core.inventory :as inventory]
            [core.math.geom :as geom]
            [core.math.raycaster :as raycaster]
            [core.math.vector :as v]
            [core.tiled :as tiled]
            [core.val-max :refer [val-max-ratio]]
            [core.world.potential-fields :as potential-fields])
  (:import com.badlogic.gdx.Input$Keys
           com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.scenes.scene2d.ui Button ButtonGroup)))

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(defn- calculate-zoom [{:keys [context/explored-tile-corners] :as ctx}]
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @explored-tile-corners)))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (camera/calculate-zoom (world-camera ctx)
                           :left left
                           :top top
                           :right right
                           :bottom bottom)))

(defn- ->tile-corner-color-setter [explored?]
  (fn tile-corner-color-setter [color x y]
    (if (get explored? [x y])
      Color/WHITE
      Color/BLACK)))

#_(deftype Screen []
    (show [_ ctx]
      (camera/set-zoom! (world-camera ctx) (calculate-zoom ctx)))

    (hide [_ ctx]
      (camera/reset-zoom! (world-camera ctx)))

    ; TODO fixme not subscreen
    (render [_ {:keys [context/tiled-map context/explored-tile-corners] :as context}]
      (tiled/render! context
                     tiled-map
                     (->tile-corner-color-setter @explored-tile-corners))
      (render-world-view context
                         (fn [g]
                           (draw-filled-circle g
                                               (camera/position (world-camera context))
                                               0.5
                                               Color/GREEN)))
      (if (or (.isKeyJustPressed gdx-input Input$Keys/TAB)
              (.isKeyJustPressed gdx-input Input$Keys/ESCAPE))
        (change-screen context :screens/world)
        context)))

#_(defcomponent :screens/minimap
  (->mk [_ _ctx]
    (->Screen)))

(defn- set-arr [arr cell* cell*->blocked?]
  (let [[x y] (:position cell*)]
    (aset arr x y (boolean (cell*->blocked? cell*)))))

(defcomponent :context/raycaster
  (->mk [[_ position->blocked?] {:keys [context/grid]}]
    (let [width  (grid2d/width  grid)
          height (grid2d/height grid)
          arr (make-array Boolean/TYPE width height)]
      (doseq [cell (grid2d/cells grid)]
        (set-arr arr @cell position->blocked?))
      (raycaster/map->ArrRayCaster {:arr arr
                                    :width width
                                    :height height}))))

; TO math.... // not tested
(defn- create-double-ray-endpositions
  "path-w in tiles."
  [[start-x start-y] [target-x target-y] path-w]
  {:pre [(< path-w 0.98)]} ; wieso 0.98??
  (let [path-w (+ path-w 0.02) ;etwas gr�sser damit z.b. projektil nicht an ecken anst�sst
        v (v/direction [start-x start-y]
                       [target-y target-y])
        [normal1 normal2] (v/get-normal-vectors v)
        normal1 (v/scale normal1 (/ path-w 2))
        normal2 (v/scale normal2 (/ path-w 2))
        start1  (v/add [start-x  start-y]  normal1)
        start2  (v/add [start-x  start-y]  normal2)
        target1 (v/add [target-x target-y] normal1)
        target2 (v/add [target-x target-y] normal2)]
    [start1,target1,start2,target2]))

(extend-type core.ctx.Context
  RayCaster
  (ray-blocked? [{:keys [context/raycaster]} start target]
    (raycaster/ray-blocked? raycaster start target))

  (path-blocked? [{:keys [context/raycaster]} start target path-w]
    (let [[start1,target1,start2,target2] (create-double-ray-endpositions start target path-w)]
      (or
       (raycaster/ray-blocked? raycaster start1 target1)
       (raycaster/ray-blocked? raycaster start2 target2)))))

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

; TODO LAZY SEQ @ grid2d/get-8-neighbour-positions !!
; https://github.com/damn/grid2d/blob/master/src/data/grid2d.clj#L126
(extend-type data.grid2d.Grid2D
  Grid
  (cached-adjacent-cells [grid cell]
    (if-let [result (:adjacent-cells @cell)]
      result
      (let [result (into [] (keep grid) (-> @cell :position grid2d/get-8-neighbour-positions))]
        (swap! cell assoc :adjacent-cells result)
        result)))

  (rectangle->cells [grid rectangle]
    (into [] (keep grid) (rectangle->tiles rectangle)))

  (circle->cells [grid circle]
    (->> circle
         geom/circle->outer-rectangle
         (rectangle->cells grid)))

  (circle->entities [grid circle]
    (->> (circle->cells grid circle)
         (map deref)
         cells->entities
         (filter #(geom/collides? circle @%)))))

(def ^:private this :context/grid)

(extend-type core.ctx.Context
  GridPointEntities
  (point->entities [ctx position]
    (when-let [cell (get (this ctx) (->tile position))]
      (filter #(geom/point-in-rect? position @%)
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
    (grid2d/create-grid width
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
           (cons idx (grid2d/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(extend-type core.ctx.Context
  ActiveEntities
  (active-entities [ctx]
    (active-entities* ctx (player-entity* ctx))))

(defcomponent content-grid
  {:let [cell-w cell-h]}
  (->mk [_ {:keys [context/grid]}]
    {:grid (grid2d/create-grid (inc (int (/ (grid2d/width grid) cell-w))) ; inc because corners
                               (inc (int (/ (grid2d/height grid) cell-h)))
                               (fn [idx]
                                 (atom {:idx idx,
                                        :entities #{}})))
     :cell-w cell-w
     :cell-h cell-h}))

(comment

 (defn get-all-entities-of-current-map [context]
   (mapcat (comp :entities deref)
           (grid2d/cells (core.context/content-grid context))))

 (count
  (get-all-entities-of-current-map @app/state))

 )

(defcomponent :context/explored-tile-corners
  (->mk [_ {:keys [context/grid]}]
    (atom (grid2d/create-grid (grid2d/width grid)
                              (grid2d/height grid)
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
                     (let [blocked? (raycaster/ray-blocked? raycaster light-position position)]
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

(defn- render-map [{:keys [context/tiled-map] :as ctx} light-position]
  (tiled/render! ctx
                 tiled-map
                 (->tile-color-setter (atom nil)
                                      light-position
                                      (:context/raycaster ctx)
                                      (:context/explored-tile-corners ctx)))
  #_(reset! do-once false))

(defn- geom-test [g ctx]
  (let [position (world-mouse-position ctx)
        grid (:context/grid ctx)
        radius 0.8
        circle {:position position :radius radius}]
    (draw-circle g position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%)
                       (circle->cells grid circle))]
      (draw-rectangle g x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (geom/circle->outer-rectangle circle)]
      (draw-rectangle g x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug [g ctx]
  (let [grid (:context/grid ctx)
        world-camera (world-camera ctx)
        [left-x right-x bottom-y top-y] (camera/frustum world-camera)]

    (when tile-grid?
      (draw-grid g (int left-x) (int bottom-y)
                 (inc (int (world-viewport-width ctx)))
                 (+ 2 (int (world-viewport-height ctx)))
                 1 1 [1 1 1 0.8]))

    (doseq [[x y] (camera/visible-tiles world-camera)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (draw-filled-rectangle g x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (draw-filled-rectangle g x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance entity]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (@#'potential-fields/factions-iterations faction))]
              (draw-filled-rectangle g x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile [g ctx]
  (when highlight-blocked-cell?
    (let [[x y] (->tile (world-mouse-position ctx))
          cell (get (:context/grid ctx) [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (draw-rectangle g x y 1 1
                        (case (:movement @cell)
                          :air  [1 1 0 0.5]
                          :none [1 0 0 0.5]))))))

(defn- before-entities [ctx g]
  (tile-debug g ctx))

(defn- after-entities [ctx g]
  #_(geom-test g ctx)
  (highlight-mouseover-tile g ctx))

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
  (for [[position creature-id] (tiled/positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(defn- spawn-creatures! [ctx tiled-level]
  (effect! ctx
           (for [creature (cons (world->player-creature ctx tiled-level)
                                (when spawn-enemies?
                                  (world->enemy-creatures ctx)))]
             [:tx/creature (update creature :position tile->middle)])))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
; done at module-gen? but not custom tiledmap?
(defn- ->world-map [{:keys [tiled-map start-position]}] ; == one object make ?! like graphics?
  ; grep context/grid -> all dependent stuff?
  (create-into {:context/tiled-map tiled-map
                :context/start-position start-position}
               {:context/grid [(tiled/width  tiled-map)
                               (tiled/height tiled-map)
                               #(case (tiled/movement-property tiled-map %)
                                  "none" :none
                                  "air"  :air
                                  "all"  :all)]
                :context/raycaster blocks-vision?
                content-grid [16 16]
                :context/explored-tile-corners true}))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (let [ctx (dissoc ctx :context/entity-tick-error)
        ctx (-> ctx
                (merge {:context/game-loop-mode mode}
                       (create-into ctx
                                    {:context/ecs true
                                     :context/time true
                                     :context/widgets true
                                     :context/effect-handler [mode record-transactions?]})))]
    (case mode
      :game-loop/normal (do
                         (when-let [tiled-map (:context/tiled-map ctx)]
                           (dispose tiled-map))
                         (-> ctx
                             (merge (->world-map tiled-level))
                             (spawn-creatures! tiled-level)))
      :game-loop/replay (merge ctx (->world-map (select-keys ctx [:context/tiled-map
                                                                  :context/start-position]))))))

(defn- start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

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

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (.isKeyJustPressed gdx-input Input$Keys/P)
      (.isKeyPressed     gdx-input Input$Keys/SPACE)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (player-state-pause-game? ctx)
                                       (not (player-unpaused?))))))

(defn- update-time [ctx delta]
  (update ctx ctx-time #(-> %
                            (assoc :delta-time delta)
                            (update :elapsed + delta)
                            (update :logic-frame inc))))

(defn- update-world [ctx]
  (let [ctx (update-time ctx (min (.getDeltaTime gdx-graphics) entity/max-delta-time))
        active-entities (active-entities ctx)]
    (potential-fields/update! ctx active-entities)
    (try (entity/tick-entities! ctx active-entities)
         (catch Throwable t
           (-> ctx
               (ui/error-window! t)
               (assoc :context/entity-tick-error t))))))

(defmulti ^:private game-loop :context/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (effect! ctx [player-update-state
                entity/update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                entity/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (logic-frame ctx)
        txs [:foo]#_(ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (effect! txs)
        #_(update :world.time/logic-frame inc))))  ; this is probably broken now (also frame->txs contains already time, so no need to inc ?)

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- render-world! [ctx]
  (camera/set-position! (world-camera ctx) (:position (player-entity* ctx)))
  (render-map ctx (camera/position (world-camera ctx)))
  (render-world-view ctx
                     (fn [g]
                       (before-entities ctx g)
                       (entity/render-entities! ctx
                                                g
                                                (->> (active-entities ctx)
                                                     (map deref)))
                       (after-entities ctx g))))

(defn- render-infostr-on-bar [g infostr x y h]
  (draw-text g {:text infostr
                :x (+ x 75)
                :y (+ y 2)
                :up? true}))

(defn- ->hp-mana-bars [context]
  (let [rahmen      (->image context "images/rahmen.png")
        hpcontent   (->image context "images/hp.png")
        manacontent (->image context "images/mana.png")
        x (/ (gui-viewport-width context) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [g ctx x y contentimg minmaxval name]
                            (draw-image g rahmen [x y])
                            (draw-image g
                                        (sub-image ctx contentimg [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh])
                                        [x y])
                            (render-infostr-on-bar g (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (ui/->actor {:draw (fn [g ctx]
                         (let [player-entity* (player-entity* ctx)
                               x (- x (/ rahmenw 2))]
                           (render-hpmana-bar g ctx x y-hp   hpcontent   (entity/stat player-entity* :stats/hp) "HP")
                           (render-hpmana-bar g ctx x y-mana manacontent (entity/stat player-entity* :stats/mana) "MP")))})))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

; TODO component to info-text move to the component itself.....
(defn- debug-infos ^String [ctx]
  (let [world-mouse (world-mouse-position ctx)]
    (str
     "logic-frame: " (logic-frame ctx) "\n"
     "FPS: " (.getFramesPerSecond gdx-graphics)  "\n"
     "Zoom: " (camera/zoom (world-camera ctx)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (gui-mouse-position ctx) "\n"
     "paused? " (:context/paused? ctx) "\n"
     "elapsed-time " (readable-number (elapsed-time ctx)) " seconds \n"
     (skill-info (player-entity* ctx))
     (when-let [entity* (mouseover-entity* ctx)]
       (str "Mouseover-entity uid: " (:entity/uid entity*)))
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (ui/mouse-on-actor? ctx)]
         (str "TRUE - name:" (.getName actor)
              "id: " (ui/actor-id actor)
              )))))

(defn- ->debug-window [context]
  (let [label (ui/->label "")
        window (ui/->window {:title "Debug"
                             :id :debug-window
                             :visible? false
                             :position [0 (gui-viewport-height context)]
                             :rows [[label]]})]
    (ui/add-actor! window (ui/->actor {:act #(do
                                              (.setText label (debug-infos %))
                                              (.pack window))}))
    window))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn- ->entity-info-window [context]
  (let [label (ui/->label "")
        window (ui/->window {:title "Info"
                             :id :entity-info-window
                             :visible? false
                             :position [(gui-viewport-width context) 0]
                             :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (ui/add-actor! window (ui/->actor {:act (fn update-label-text [ctx]
                                              ; items then have 2x pretty-name
                                              #_(.setText (.getTitleLabel window)
                                                          (if-let [entity* (mouseover-entity* ctx)]
                                                            (info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                            "Entity Info"))
                                              (.setText label
                                                        (str (when-let [entity* (mouseover-entity* ctx)]
                                                               (->info-text
                                                                ; don't use select-keys as it loses core.entity.Entity record type
                                                                (apply dissoc entity* disallowed-keys)
                                                                ctx))))
                                              (.pack window))}))
    window))

(def ^:private image-scale 2)

(defn- ->action-bar []
  (let [group (ui/->horizontal-group {:pad 2 :space 2})]
    (ui/set-id! group ::action-bar)
    group))

(defn- ->action-bar-button-group []
  (ui/->button-group {:max-check-count 1
                      :min-check-count 0}))

(defn- get-action-bar [ctx]
  {:horizontal-group (::action-bar (:action-bar-table (ui/stage-get ctx)))
   :button-group (:action-bar (:context/widgets ctx))})

(defcomponent :tx.action-bar/add
  (do! [[_ {:keys [property/id entity/image] :as skill}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (ui/->image-button image identity {:scale image-scale})]
      (ui/set-id! button id)
      (ui/add-tooltip! button
                          #(->info-text skill (assoc % :effect/source (player-entity %))))
      (ui/add-actor! horizontal-group button)
      (.add ^ButtonGroup button-group ^Button button)
      ctx)))

(defcomponent :tx.action-bar/remove
  (do! [[_ {:keys [property/id]}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (get horizontal-group id)]
      (ui/remove! button)
      (.remove ^ButtonGroup button-group ^Button button)
      ctx)))

(comment

 (comment
  (def sword-button (.getChecked button-group))
  (.setChecked sword-button false)
  )

 #_(defn- number-str->input-key [number-str]
     (eval (symbol (str "com.badlogic.gdx.Input$Keys/NUM_" number-str))))

 ; TODO do with an actor
 ; .getChildren horizontal-group => in order
 (defn up-skill-hotkeys []
   #_(doseq [slot slot-keys
             :let [skill-id (slot @slot->skill-id)]
             :when (and (.isKeyJustPressed gdx-input (number-str->input-key (name slot)))
                        skill-id)]
       (.setChecked ^Button (.findActor horizontal-group (str skill-id)) true)))

 ; TODO
 ; * cooldown / not usable -> diff. colors ? disable on not able to use skills (stunned?)
 ; * or even sector circling for cooldown like in WoW (clipped !)
 ; * tooltips ! with hotkey-number !
 ;  ( (skills/text skill-id player-entity))
 ; * add hotkey number to tooltips
 ; * hotkeys => select button
 ; when no selected-skill & new skill assoce'd (sword at start)
 ; => set selected
 ; keep weapon at position 1 always ?

 #_(def ^:private slot-keys {:1 input.keys.num-1
                             :2 input.keys.num-2
                             :3 input.keys.num-3
                             :4 input.keys.num-4
                             :5 input.keys.num-5
                             :6 input.keys.num-6
                             :7 input.keys.num-7
                             :8 input.keys.num-8
                             :9 input.keys.num-9})

 #_(defn- empty-slot->skill-id []
     (apply sorted-map
            (interleave slot-keys
                        (repeat nil))))

 #_(def selected-skill-id (atom nil))
 #_(def ^:private slot->skill-id (atom nil))

 #_(defn reset-skills! []
     (reset! selected-skill-id nil)
     (reset! slot->skill-id (empty-slot->skill-id)))


 ; https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/ui/Button.html
 (.setProgrammaticChangeEvents ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; but doesn't toggle:
 (.toggle ^Button (.findActor horizontal-group ":skills/spawn"))
 (.setChecked ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; Toggles the checked state. This method changes the checked state, which fires a ChangeListener.ChangeEvent (if programmatic change events are enabled), so can be used to simulate a button click.

 ; => it _worked_ => active skill changed
 ; only button is not highlighted idk why

 (.getChildren horizontal-group)

 )

(defn- ->ui-actors [ctx widget-data]
  [(ui/->table {:rows [[{:actor (->action-bar)
                         :expand? true
                         :bottom? true}]]
                :id :action-bar-table
                :cell-defaults {:pad 2}
                :fill-parent? true})
   (->hp-mana-bars ctx)
   (ui/->group {:id :windows
                :actors [(->debug-window ctx)
                         (->entity-info-window ctx)
                         (inventory/->build ctx widget-data)]})
   (ui/->actor {:draw draw-item-on-cursor})
   (->mk [:widgets/player-message] ctx)])


(defcomponent :context/widgets
  (->mk [_ ctx]
    (let [widget-data {:action-bar (->action-bar-button-group)
                       :slot->background (inventory/->data ctx)}
          stage (ui/stage-get ctx)]
      (.clear stage)
      (run! #(.addActor stage %) (->ui-actors ctx widget-data))
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config]}]
  (merge {Input$Keys/I :inventory-window
          Input$Keys/E :entity-info-window}
         (when (safe-get config :debug-window?)
           {Input$Keys/Z :debug-window})))

(defn- check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id ctx)
          :when (.isKeyJustPressed gdx-input hotkey)]
    (ui/toggle-visible! (get (:windows (ui/stage-get ctx)) window-id))))

(defn- close-windows?! [context]
  (let [windows (ui/children (:windows (ui/stage-get context)))]
    (if (some ui/visible? windows)
      (do
       (run! #(ui/set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (world-camera ctx)]
    (when (.isKeyPressed gdx-input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed gdx-input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
             (not (close-windows?! ctx)))
        (change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(.isKeyJustPressed gdx-input Input$Keys/TAB)
        #_(change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent :world/sub-screen
  (screen-exit [_ ctx]
    (set-cursor! ctx :cursors/default))

  (screen-render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage)
(defcomponent :screens/world
  (->mk [_ ctx]
    {:stage (ui/->stage ctx [])
     :sub-screen [:world/sub-screen]}))

(comment

 ; https://github.com/damn/core/issues/32
 ; grep :game-loop/replay
 ; unused code & not working
 ; record only top-lvl txs or save world state every x minutes/seconds

 ; TODO @replay-mode
 ; * do I need check-key-input from screens/world?
 ; adjust sound speed also equally ? pitch ?
 ; player message, player modals, etc. all game related state handle ....
 ; game timer is not reset  - continues as if
 ; check other atoms , try to remove atoms ...... !?
 ; replay mode no window hotkeys working
 ; buttons working
 ; can remove items from inventory ! changes cursor but does not change back ..
 ; => deactivate all input somehow (set input processor nil ?)
 ; works but ESC is separate from main input processor and on re-entry
 ; again stage is input-processor
 ; also cursor is from previous game replay
 ; => all hotkeys etc part of stage input processor make.
 ; set nil for non idle/item in hand states .
 ; for some reason he calls end of frame checks but cannot open windows with hotkeys

 (defn- start-replay-mode! [ctx]
   (.setInputProcessor gdx-input nil)
   (init-game-context ctx :mode :game-loop/replay))

 (.postRunnable gdx-app
  (fn []
    (swap! app-state start-replay-mode!)))

 )

(defn- start-game! [world-id]
  (fn [ctx]
    (-> ctx
        (change-screen :screens/world)
        (start-new-game (level-generator/->world ctx world-id)))))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ui/->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (property/all-properties ctx :properties/worlds)]
                                     [(ui/->text-button (str "Start " id) (start-game! id))])
                                   [(when (safe-get config :map-editor?)
                                      [(ui/->text-button "Map editor" #(change-screen % :screens/map-editor))])
                                    (when (safe-get config :property-editor?)
                                      [(ui/->text-button "Property editor" #(change-screen % :screens/property-editor))])
                                    [(ui/->text-button "Exit" (fn [ctx] (.exit gdx-app) ctx))]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))


(defcomponent :main/sub-screen
  (screen-enter [_ ctx]
    (set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(ui/->background-image ctx)
   (->buttons ctx)
   (ui/->actor {:act (fn [_ctx]
                       (when (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
                         (.exit gdx-app)))})])

(derive :screens/main-menu :screens/stage)
(defcomponent :screens/main-menu
  (->mk [[k _] ctx]
    {:sub-screen [:main/sub-screen]
     :stage (ui/->stage ctx (->actors ctx))}))

(defprotocol StatusCheckBox
  (get-text [this])
  (get-state [this])
  (set-state [this is-selected]))

(deftype VarStatusCheckBox [^clojure.lang.Var avar]
  StatusCheckBox
  (get-text [this]
    (let [m (meta avar)]
      (str "[LIGHT_GRAY]" (str (:ns m)) "/[WHITE]" (name (:name m)) "[]")))

  (get-state [this]
    @avar)

  (set-state [this is-selected]
    (.bindRoot avar is-selected)))

(defn- debug-flags [] ;
  (apply concat
         ; TODO
         (for [nmspace (utils/get-namespaces #{"core"})] ; DRY in core.component check ns-name & core.app require all ... core.components
           (utils/get-vars nmspace (fn [avar] (:dbg-flag (meta avar)))))))

; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
; -. rebuild it on window open ...
(def ^:private debug-flags (map ->VarStatusCheckBox (debug-flags)))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table [{:keys [context/config] :as ctx}]
  (ui/->table {:rows (concat
                      [[(ui/->label key-help-text)]]

                      (when (safe-get config :debug-window?)
                        [[(ui/->label "[Z] - Debug window")]])

                      (when (safe-get config :debug-options?)
                        (for [check-box debug-flags]
                          [(ui/->check-box (get-text check-box)
                                           (partial set-state check-box)
                                           (boolean (get-state check-box)))]))

                      [[(ui/->text-button "Resume" #(change-screen % :screens/world))]

                       [(ui/->text-button "Exit" #(change-screen % :screens/main-menu))]])

               :fill-parent? true
               :cell-defaults {:pad-bottom 10}}))

(defcomponent :options/sub-screen
  (screen-render [_ ctx]
    (if (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
      (change-screen ctx :screens/world)
      ctx)))

(derive :screens/options-menu :screens/stage)
(defcomponent :screens/options-menu
  (->mk [_ ctx]
    {:stage (ui/->stage ctx [(ui/->background-image ctx) (create-table ctx)])
     :sub-screen [:options/sub-screen]}))
