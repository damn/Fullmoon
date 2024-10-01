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

(defn- ->world-time []
  {:elapsed 0
   :logic-frame 0})

(defn update-time [delta]
  (update ctx :context/time #(-> %
                                 (assoc :delta-time delta)
                                 (update :elapsed + delta)
                                 (update :logic-frame inc))))

(defn- ->explored-tile-corners [width height]
  (atom (g/create-grid width height (constantly false))))

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (t/movement-property tiled-map position)
      "none" :none
      "air"  :air
      "all"  :all)))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
(defn- ->world-map [{:keys [tiled-map start-position]}]
  (let [w (t/width  tiled-map)
        h (t/height tiled-map)
        grid (->world-grid w h (world-grid-position->value-fn tiled-map))]
    {:context/tiled-map tiled-map
     :context/start-position start-position
     :context/grid grid

     ; TODO the keyword needs to be in ->raycaster
     ; as the code there depends on that k specifically
     :context/raycaster (->raycaster grid blocks-vision?)
     content-grid (->content-grid :cell-size 16 :width w :height h)
     :context/explored-tile-corners (->explored-tile-corners w h)}))

(.bindRoot #'clojure.gdx/add-world-ctx
           (fn [world-property-id]
             (when-let [tiled-map (:context/tiled-map)]
               (dispose! tiled-map))
             (let [tiled-level (generate-level ctx world-property-id)]
               (-> ctx
                   (dissoc :context/entity-tick-error)
                   (assoc :context/ecs (->uids-entities)
                          :context/time (->world-time)
                          :context/widgets (->world-widgets))
                   (merge (->world-map tiled-level))
                   (spawn-creatures! tiled-level)))))

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
