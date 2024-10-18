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
            [gdx.tiled :as t]
            [gdx.utils :refer [dispose!]]
            [utils.core :refer [bind-root ->tile tile->middle safe-merge sort-by-order]]
            [world.core.content-grid :as content-grid]
            [world.core.raycaster :as raycaster]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [level.tiled :refer [movement-property]])
  (:load "core/grid"
         "core/potential_fields"
         "core/time"))

(defn- init-raycaster []
  (def ^:private raycaster (raycaster/create grid blocks-vision?)))

(defn ray-blocked? [start target]
  (raycaster/blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [start target path-w]
  (raycaster/path-blocked? raycaster start target path-w))

(declare ^:private content-grid
         player)

(defn- init-content-grid! [opts]
  (bind-root #'content-grid (content-grid/create opts)))

(defn active-entities []
  (content-grid/active-entities content-grid @player))

(load "core/entities"
      "core/mouseover_entity"
      "core/debug_render"
      "core/render")

(declare ^:private entity-tick-error)

(defn init! [tiled-map]
  (init-tiled-map tiled-map)
  (bind-root #'entity-tick-error nil)
  (init-time!)
  (let [w (t/width  tiled-map)
        h (t/height tiled-map)]
    (init-grid! w h (fn [position]
                      (case (movement-property tiled-map position)
                        "none" :none
                        "air"  :air
                        "all"  :all)))
    (init-raycaster)
    (init-content-grid! {:cell-size 16 :width w :height h})
    (init-explored-tile-corners w h))
  (init-ids->eids))

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
