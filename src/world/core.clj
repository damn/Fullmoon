(ns world.core
  (:require [clj-commons.pretty.repl :refer [pretty-pst]]
            [component.core :refer [defc]]
            [component.db :as db]
            [component.tx :as tx]
            [data.grid2d :as g2d]
            [gdx.graphics :as g :refer [white black]]
            [gdx.graphics.camera :as 🎥]
            [gdx.input :refer [key-pressed? key-just-pressed?]]
            [gdx.ui.error-window :refer [error-window!]]
            [gdx.ui.stage-screen :as stage-screen]
            [gdx.math.shape :as shape]
            [gdx.math.vector :as v]
            [gdx.tiled :as t]
            [gdx.utils :refer [dispose!]]
            [utils.core :refer [bind-root ->tile tile->middle safe-merge sort-by-order]]
            [world.core.content-grid :as content-grid]
            [world.core.raycaster :as raycaster]
            [world.entity :as entity]
            [world.entity.state :as entity-state]

            [level.tiled :refer [movement-property]]))

(load "core/grid"
      "core/potential_fields"
      "core/time")

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
    (case (movement-property tiled-map position)
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

(load "core/mouseover_entity")

(load "core/debug_render")

(load "core/render")

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
