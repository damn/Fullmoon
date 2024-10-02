(ns core.world
  (:require [clojure.gdx :refer :all]
            [clojure.gdx.rand :refer [get-rand-weighted-item]]
            [clojure.gdx.tiled :as t]
            [clojure.string :as str]
            [data.grid2d :as g])
  (:load "world/modules" ; very specific and hardcoded stuff
         "world/generators" ; unfinished
         "world/cached_renderer" ; move ?
         "world/editor_screen" ; move, but very raw
         "world/spawn" ; ?
         "world/widgets"))
;;; ?

(defn- bind-world-time! []
  (.bindRoot #'elapsed-time 0)
  (.bindRoot #'logic-frame 0))

(defn update-time [delta]
  (.bindRoot #'world-delta delta)
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

; TODO  add-to-world/assoc/dissoc uid from entity move together here
; also core.screens/world ....
(.bindRoot #'clojure.gdx/add-world-ctx
           (fn [world-property-id]
             (when (bound? #'world-tiled-map)
               (dispose! world-tiled-map))
             (bind-world-time!)
             (.bindRoot #'world-widgets (->world-widgets))
             (let [{:keys [tiled-map start-position]} (generate-level world-property-id)
                   w (t/width  tiled-map)
                   h (t/height tiled-map)
                   grid (->world-grid w h (world-grid-position->value-fn tiled-map))]
               (.bindRoot #'world-grid grid)
               (.bindRoot #'world-raycaster (->raycaster grid blocks-vision?))
               (.bindRoot #'world-tiled-map tiled-map)
               (.bindRoot #'content-grid (->content-grid :cell-size 16 :width w :height h))
               (.bindRoot #'explored-tile-corners (->explored-tile-corners w h))
               (.bindRoot #'entity-tick-error nil)
               (.bindRoot #'uids-entities {})
               (spawn-creatures! tiled-map start-position))))

(defcomponent :tx/add-to-world
  (do! [[_ entity]]
    (content-grid-update-entity! entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid-add-entity! entity)))

(defcomponent :tx/remove-from-world
  (do! [[_ entity]]
    (content-grid-remove-entity! entity)
    (grid-remove-entity! entity)))

(defcomponent :tx/position-changed
  (do! [[_ entity]]
    (content-grid-update-entity! entity)
    (grid-entity-position-changed! entity)))
