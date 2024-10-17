(ns world.core
  (:require [clj-commons.pretty.repl :refer [pretty-pst]]
            [component.core :refer [defc]]
            [component.db :as db]
            [component.tx :as tx]
            [data.grid2d :as g2d]
            [gdx.graphics :as g :refer [white black]]
            [gdx.graphics.camera :as 🎥]
            [gdx.input :refer [key-pressed? key-just-pressed?]]
            [gdx.ui.stage-screen :as stage-screen]
            [gdx.math.shape :as shape]
            [gdx.math.vector :as v]
            [gdx.tiled :as t]
            [gdx.utils :refer [dispose!]]
            [utils.core :refer [bind-root ->tile tile->middle safe-merge sort-by-order]]
            [world.content-grid :as content-grid]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [world.raycaster :as raycaster]
            [world.widgets.error :refer [error-window!]]))

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

(declare grid)

(defn rectangle->cells [rectangle]
  (into [] (keep grid) (rectangle->tiles rectangle)))

(defn circle->cells [circle]
  (->> circle
       shape/circle->outer-rectangle
       rectangle->cells))

(defn cells->entities [cells]
  (into #{} (mapcat :entities) cells))

(defn circle->entities [circle]
  (->> (circle->cells circle)
       (map deref)
       cells->entities
       (filter #(shape/overlaps? circle @%))))

(defn- set-cells! [eid]
  (let [cells (rectangle->cells @eid)]
    (assert (not-any? nil? cells))
    (swap! eid assoc ::touched-cells cells)
    (doseq [cell cells]
      (assert (not (get (:entities @cell) eid)))
      (swap! cell update :entities conj eid))))

(defn- remove-from-cells! [eid]
  (doseq [cell (::touched-cells @eid)]
    (assert (get (:entities @cell) eid))
    (swap! cell update :entities disj eid)))

; could use inside tiles only for >1 tile bodies (for example size 4.5 use 4x4 tiles for occupied)
; => only now there are no >1 tile entities anyway
(defn- rectangle->occupied-cells [{:keys [left-bottom width height] :as rectangle}]
  (if (or (> (float width) 1) (> (float height) 1))
    (rectangle->cells rectangle)
    [(get grid
          [(int (+ (float (left-bottom 0)) (/ (float width) 2)))
           (int (+ (float (left-bottom 1)) (/ (float height) 2)))])]))

(defn- set-occupied-cells! [eid]
  (let [cells (rectangle->occupied-cells @eid)]
    (doseq [cell cells]
      (assert (not (get (:occupied @cell) eid)))
      (swap! cell update :occupied conj eid))
    (swap! eid assoc ::occupied-cells cells)))

(defn- remove-from-occupied-cells! [eid]
  (doseq [cell (::occupied-cells @eid)]
    (assert (get (:occupied @cell) eid))
    (swap! cell update :occupied disj eid)))

; TODO LAZY SEQ @ g/get-8-neighbour-positions !!
; https://github.com/damn/g/blob/master/src/data/grid2d.clj#L126
(defn cached-adjacent-cells [cell]
  (if-let [result (:adjacent-cells @cell)]
    result
    (let [result (into [] (keep grid) (-> @cell :position g2d/get-8-neighbour-positions))]
      (swap! cell assoc :adjacent-cells result)
      result)))

(defn point->entities [position]
  (when-let [cell (get grid (->tile position))]
    (filter #(shape/contains? @% position)
            (:entities @cell))))

(defn- grid-add-entity! [eid]
  (set-cells! eid)
  (when (:collides? @eid)
    (set-occupied-cells! eid)))

(defn- grid-remove-entity! [eid]
  (remove-from-cells! eid)
  (when (:collides? @eid)
    (remove-from-occupied-cells! eid)))

(defn- grid-entity-position-changed! [eid]
  (remove-from-cells! eid)
  (set-cells! eid)
  (when (:collides? @eid)
    (remove-from-occupied-cells! eid)
    (set-occupied-cells! eid)))

(defprotocol GridCell
  (blocked? [cell* z-order])
  (blocks-vision? [cell*])
  (occupied-by-other? [cell* eid]
                      "returns true if there is some occupying body with center-tile = this cell
                      or a multiple-cell-size body which touches this cell.")
  (nearest-entity          [cell* faction])
  (nearest-entity-distance [cell* faction]))

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

  (occupied-by-other? [_ eid]
    (some #(not= % eid) occupied)) ; contains? faster?

  (nearest-entity [this faction]
    (-> this faction :eid))

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

(defn- init-grid! [width height position->value]
  (bind-root #'grid (g2d/create-grid width
                                     height
                                     #(atom (create-cell % (position->value %))))))

(load "potential_fields")

(declare paused?

         ^{:doc "The game logic update delta-time. Different then gdx.graphics/delta-time because it is bounded by a maximum value for entity movement speed."}
         delta-time

         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time

         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defn- init-time! []
  (bind-root #'elapsed-time 0)
  (bind-root #'logic-frame 0))

(defn- update-time! [delta]
  (bind-root #'delta-time delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defn timer [duration]
  {:pre [(>= duration 0)]}
  {:duration duration
   :stop-time (+ elapsed-time duration)})

(defn stopped? [{:keys [stop-time]}]
  (>= elapsed-time stop-time))

(defn reset [{:keys [duration] :as counter}]
  (assoc counter :stop-time (+ elapsed-time duration)))

(defn finished-ratio [{:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time elapsed-time) duration))))

(declare player
         tiled-map
         ^:private entity-tick-error
         explored-tile-corners)

(defn- init-explored-tile-corners! [width height]
  (bind-root #'explored-tile-corners (atom (g2d/create-grid width height (constantly false)))))

;;

(declare ^:private raycaster)

(defn- init-raycaster! []
  (bind-root #'raycaster (raycaster/create grid blocks-vision?)))

(defn ray-blocked? [start target]
  (raycaster/blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [start target path-w]
  (raycaster/path-blocked? raycaster start target path-w))

;;

(declare ^:private content-grid)

(defn- init-content-grid! [opts]
  (bind-root #'content-grid (content-grid/create opts)))

(defn active-entities []
  (content-grid/active-entities content-grid @player))

;;

(declare ^:private ids->eids)

(defn- init-ids->eids! []
  (bind-root #'ids->eids {}))

(defn all-entities []
  (vals ids->eids))

(defn get-entity
  "Mostly used for debugging, use an entity's atom for (probably) faster access in your logic."
  [id]
  (get ids->eids id))

;;

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (t/movement-property tiled-map position)
      "none" :none
      "air"  :air
      "all"  :all)))

(defc :tx/add-to-world
  (tx/do! [[_ eid]]
    (let [id (:entity/id @eid)]
      (assert (number? id))
      (alter-var-root #'ids->eids assoc id eid))
    (content-grid/update-entity! content-grid eid)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @eid)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid-add-entity! eid)
    nil))

(defc :tx/remove-from-world
  (tx/do! [[_ eid]]
    (let [id (:entity/id @eid)]
      (assert (contains? ids->eids id))
      (alter-var-root #'ids->eids dissoc id))
    (content-grid/remove-entity! eid)
    (grid-remove-entity! eid)
    nil))

(defc :tx/position-changed
  (tx/do! [[_ eid]]
    (content-grid/update-entity! content-grid eid)
    (grid-entity-position-changed! eid)
   nil))

(defn init! [tiled-map]
  (bind-root #'entity-tick-error nil)
  (init-time!)
  (when (bound? #'tiled-map)
    (dispose! @#'tiled-map))
  (bind-root #'tiled-map tiled-map)
  (let [w (t/width  tiled-map)
        h (t/height tiled-map)]
    (init-grid! w h (world-grid-position->value-fn tiled-map))
    (init-raycaster!)
    (init-content-grid! {:cell-size 16 :width w :height h})
    (init-explored-tile-corners! w h))
  (init-ids->eids!))

;;

(defn- geom-test []
  (let [position (g/world-mouse-position)
        radius 0.8
        circle {:position position :radius radius}]
    (g/draw-circle position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%) (circle->cells circle))]
      (g/draw-rectangle x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (shape/circle->outer-rectangle circle)]
      (g/draw-rectangle x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug []
  (let [🎥 (g/world-camera)
        [left-x right-x bottom-y top-y] (🎥/frustum 🎥)]

    (when tile-grid?
      (g/draw-grid (int left-x) (int bottom-y)
                   (inc (int (g/world-viewport-width)))
                   (+ 2 (int (g/world-viewport-height)))
                   1 1 [1 1 1 0.8]))

    (doseq [[x y] (🎥/visible-tiles 🎥)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (g/draw-filled-rectangle x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (g/draw-filled-rectangle x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (factions-iterations faction))]
              (g/draw-filled-rectangle x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

;;

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile []
  (when highlight-blocked-cell?
    (let [[x y] (->tile (g/world-mouse-position))
          cell (get grid [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (g/draw-rectangle x y 1 1
                          (case (:movement @cell)
                            :air  [1 1 0 0.5]
                            :none [1 0 0 0.5]))))))

(defn- render-before-entities [] (tile-debug))

(defn- render-after-entities []
  #_(geom-test)
  (highlight-mouseover-tile))

;;

(def ^:private explored-tile-color (g/->color 0.5 0.5 0.5 1))

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

(defn- ->tile-color-setter [light-cache light-position]
  (fn tile-color-setter [_color x y]
    (let [position [(int x) (int y)]
          explored? (get @explored-tile-corners position) ; TODO needs int call ?
          base-color (if explored? explored-tile-color black)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (ray-blocked? light-position position)]
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

(defn- render-tiled-map! [light-position]
  (g/draw-tiled-map tiled-map
                    (->tile-color-setter (atom nil) light-position))
  #_(reset! do-once false))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity]
  (let [[x y] (:position entity)
        x (float x)
        y (float y)
        [cx cy] (🎥/position (g/world-camera))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (g/world-viewport-width))  2)))
     (<= ydist (inc (/ (float (g/world-viewport-height)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [source target]
  (and (or (not (:entity/player? source))
           (on-screen? target))
       (not (and los-checks?
                 (ray-blocked? (:position source) (:position target))))))

(defn- remove-destroyed-entities!
  "Calls destroy on all entities which are marked with ':e/destroy'"
  []
  (mapcat (fn [eid]
            (cons [:tx/remove-from-world eid]
                  (for [component @eid]
                    #(entity/destroy component eid))))
          (filter (comp :entity/destroyed? deref) (all-entities))))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- create-vs
  "Creates a map for every component with map entries `[k (create [k v])]`."
  [components]
  (reduce (fn [m [k v]]
            (assoc m k (entity/->v [k v])))
          {}
          components))

(defc :e/create
  (tx/do! [[_ position body components]]
    (assert (and (not (contains? components :position))
                 (not (contains? components :entity/id))))
    (let [eid (atom (-> body
                        (assoc :position position)
                        entity/->Body
                        (safe-merge (-> components
                                        (assoc :entity/id (unique-number!))
                                        (create-vs)))))]
      (cons [:tx/add-to-world eid]
            (for [component @eid]
              #(entity/create component eid))))))

(defc :e/destroy
  (tx/do! [[_ eid]]
    [[:e/assoc eid :entity/destroyed? true]]))

(defc :e/assoc
  (tx/do! [[_ eid k v]]
    (assert (keyword? k))
    (swap! eid assoc k v)
    nil))

(defc :e/assoc-in
  (tx/do! [[_ eid ks v]]
    (swap! eid assoc-in ks v)
    nil))

(defc :e/dissoc
  (tx/do! [[_ eid k]]
    (assert (keyword? k))
    (swap! eid dissoc k)
    nil))

(defc :e/dissoc-in
  (tx/do! [[_ eid ks]]
    (assert (> (count ks) 1))
    (swap! eid update-in (drop-last ks) dissoc (last ks))
    nil))

(defc :e/update-in
  (tx/do! [[_ eid ks f]]
    (swap! eid update-in ks f)
    nil))

;;

(def ^:private ^:dbg-flag pausing? true)

(defn- player-state-pause-game? [] (entity-state/pause-game? (entity-state/state-obj @player)))
(defn- player-update-state      [] (entity-state/manual-tick (entity-state/state-obj @player)))

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space))) ; FIXMe :keys? shouldnt it be just :space?

(defn- update-game-paused []
  (bind-root #'paused? (or entity-tick-error
                                 (and pausing?
                                      (player-state-pause-game?)
                                      (not (player-unpaused?)))))
  nil)

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [entity color]
  (let [[x y] (:left-bottom entity)]
    (g/draw-rectangle x y (:width entity) (:height entity) color)))

(defn- render-entity! [system entity]
  (try
   (when show-body-bounds
     (draw-body-rect entity (if (:collides? entity) :white :gray)))
   (run! #(system % entity) entity)
   (catch Throwable t
     (draw-body-rect entity :red)
     (pretty-pst t 12))))

(defn- render-entities!
  "Draws entities in the correct z-order and in the order of render-systems for each z-order."
  [entities]
  (let [player-entity @player]
    (doseq [[z-order entities] (sort-by-order (group-by :z-order entities)
                                               first
                                               entity/render-order)
            system entity/render-systems
            entity entities
            :when (or (= z-order :z-order/effect)
                      (line-of-sight? player-entity entity))]
      (render-entity! system entity))))

; precaution in case a component gets removed by another component
; the question is do we still want to update nil components ?
; should be contains? check ?
; but then the 'order' is important? in such case dependent components
; should be moved together?
(defn- tick-system [eid]
  (try
   (doseq [k (keys @eid)]
     (when-let [v (k @eid)]
       (tx/do-all (entity/tick [k v] eid))))
   (catch Throwable t
     (throw (ex-info "" (select-keys @eid [:entity/id]) t)))))

(defn- tick-entities!
  "Calls tick system on all components of entities."
  [entities]
  (run! tick-system entities))

(load "mouseover_entity")

(defn tick! []
  (🎥/set-position! (g/world-camera) (:position @player))
  (render-tiled-map! (🎥/position (g/world-camera)))
  (g/render-world-view! (fn []
                          (render-before-entities)
                          (render-entities! (map deref (active-entities)))
                          (render-after-entities)))
  (tx/do-all [player-update-state
              ; this do always so can get debug info even when game not running
              update-mouseover-entity!
              update-game-paused
              #(when-not paused?
                 (update-time! (min (g/delta-time) entity/max-delta-time))
                 (let [entities (active-entities)]
                   (update-potential-fields! entities)
                   (try (run! tick-system entities)
                        (catch Throwable t
                          (error-window! t)
                          (bind-root #'entity-tick-error t))))
                 nil)
              ; do not pause this as for example pickup item, should be destroyed.
              remove-destroyed-entities!]))
