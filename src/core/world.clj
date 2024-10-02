(ns core.world
  (:require [clojure.gdx :refer :all]
            [clojure.gdx.rand :refer [get-rand-weighted-item]]
            [clojure.gdx.tiled :as t]
            [clojure.string :as str]
            [data.grid2d :as g])
  (:load "world/modules"
         "world/generators"
         "world/cached_renderer"
         "world/editor_screen"
         "world/spawn"
         "world/widgets"))

(defn- init-world-time! []
  (bind-root #'elapsed-time 0)
  (bind-root #'logic-frame 0))

(defn update-time [delta]
  (bind-root #'world-delta delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defn- ->explored-tile-corners [width height]
  (atom (g/create-grid width height (constantly false))))

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (t/movement-property tiled-map position)
      "none" :none
      "air"  :air
      "all"  :all)))

(declare entity-tick-error)

(defn- cleanup-last-world! []
  (when (bound? #'world-tiled-map)
    (dispose! world-tiled-map)))

(defn- init-new-world! [world-property-id]
  (init-world-time!)
  (bind-root #'world-widgets (->world-widgets))
  (let [{:keys [tiled-map start-position]} (generate-level world-property-id)
        w (t/width  tiled-map)
        h (t/height tiled-map)
        grid (->world-grid w h (world-grid-position->value-fn tiled-map))]
    (bind-root #'world-grid grid)
    (bind-root #'world-raycaster (->raycaster grid blocks-vision?))
    (bind-root #'world-tiled-map tiled-map)
    (bind-root #'content-grid (->content-grid :cell-size 16 :width w :height h))
    (bind-root #'explored-tile-corners (->explored-tile-corners w h))
    (bind-root #'entity-tick-error nil)
    (bind-root #'uids-entities {})
    (spawn-creatures! tiled-map start-position)))

; TODO  add-to-world/assoc/dissoc uid from entity move together here
; also core.screens/world ....
(bind-root #'clojure.gdx/add-world-ctx
           (fn [world-property-id]
             (cleanup-last-world!)
             (init-new-world! world-property-id)))

(defcomponent :tx/add-to-world
  (do! [[_ entity]]
    (content-grid-update-entity! entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid-add-entity! entity)
    nil))

(defcomponent :tx/remove-from-world
  (do! [[_ entity]]
    (content-grid-remove-entity! entity)
    (grid-remove-entity! entity)
    nil))

(defcomponent :tx/position-changed
  (do! [[_ entity]]
    (content-grid-update-entity! entity)
    (grid-entity-position-changed! entity)
    nil))
