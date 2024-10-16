(ns world.core
  (:require [clojure.gdx.math.shape :as shape]
            [clojure.gdx.tiled :as t]
            [clojure.gdx.utils :refer [dispose!]]
            [core.component :refer [defc]]
            [core.tx :as tx]
            [data.grid2d :as g2d]
            [utils.core :refer [->tile tile->middle]]
            [world.content-grid :as content-grid]
            [world.raycaster :as raycaster]))

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
  (.bindRoot #'grid (g2d/create-grid width
                                     height
                                     #(atom (create-cell % (position->value %))))))

(declare paused?)

(declare ^{:doc "The game logic update delta-time. Different then clojure.gdx.graphics/delta-time because it is bounded by a maximum value for entity movement speed."}
         delta-time

         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time

         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defn- init-time! []
  (.bindRoot #'elapsed-time 0)
  (.bindRoot #'logic-frame 0))

(defn update-time! [delta]
  (.bindRoot #'delta-time delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defn ->counter [duration]
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
         entity-tick-error)

(declare explored-tile-corners)

(defn- init-explored-tile-corners! [width height]
  (.bindRoot #'explored-tile-corners (atom (g2d/create-grid width height (constantly false)))))

(declare ^:private raycaster)

(defn- init-raycaster! []
  (.bindRoot #'raycaster (raycaster/create grid blocks-vision?)))

(defn ray-blocked? [start target]
  (raycaster/blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [start target path-w]
  (raycaster/path-blocked? raycaster start target path-w))

(declare ^:private content-grid)

(defn- init-content-grid! [opts]
  (.bindRoot #'content-grid (content-grid/create opts)))

(defn active-entities []
  (content-grid/active-entities content-grid @player))

(declare ^:private ids->eids)

(defn- init-ids->eids! []
  (.bindRoot #'ids->eids {}))

(defn all-entities []
  (vals ids->eids))

(defn get-entity
  "Mostly used for debugging, use an entity's atom for (probably) faster access in your logic."
  [id]
  (get ids->eids id))

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (t/movement-property tiled-map position)
      "none" :none
      "air"  :air
      "all"  :all)))

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
(defn- world->player-creature [start-position]
  {:position start-position
   :creature-id :creatures/vampire
   :components player-components})

(defn- world->enemy-creatures [tiled-map]
  (for [[position creature-id] (t/positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(defn- spawn-creatures! [tiled-map start-position]
  (tx/do-all (for [creature (cons (world->player-creature start-position)
                                  (when spawn-enemies?
                                    (world->enemy-creatures tiled-map)))]
               [:tx/creature (update creature :position tile->middle)])))

(defn init! [{:keys [tiled-map start-position]}]
  (.bindRoot #'entity-tick-error nil)
  (init-time!)
  (when (bound? #'tiled-map)
    (dispose! @#'tiled-map))
  (.bindRoot #'tiled-map tiled-map)
  (let [w (t/width  tiled-map)
        h (t/height tiled-map)]
    (init-grid! w h (world-grid-position->value-fn tiled-map))
    (init-raycaster!)
    (init-content-grid! {:cell-size 16 :width w :height h})
    (init-explored-tile-corners! w h))
  (init-ids->eids!)
  (spawn-creatures! tiled-map start-position))

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
