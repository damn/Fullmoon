(ns core.world
  (:require [clojure.gdx :refer :all]
            [clojure.gdx.tiled :as t]
            [clojure.string :as str]
            [data.grid2d :as g])
  (:load "world/helper"
         "world/modules"
         "world/areas"
         "world/generators"
         "world/cached_renderer"
         "world/editor_screen"
         ; used @ txs ...
         "world/grid"
         "world/content_grid"
         "world/spawn"
         "world/widgets"))

(defn- ->world-time []
  {:elapsed 0
   :logic-frame 0})

(defn update-time [ctx delta]
  (update ctx :context/time #(-> %
                                 (assoc :delta-time delta)
                                 (update :elapsed + delta)
                                 (update :logic-frame inc))))

(defn- ->explored-tile-corners [width height]
  (atom (g/create-grid width height (constantly false))))

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (movement-property tiled-map position)
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

(extend-type clojure.gdx.Context
  WorldContext
  (add-world-ctx [ctx world-property-id]
    (when-let [tiled-map (:context/tiled-map ctx)]
      (dispose! tiled-map))
    (let [tiled-level (generate-level ctx world-property-id)]
      (-> ctx
          (dissoc :context/entity-tick-error)
          (assoc :context/ecs (->uids-entities)
                 :context/time (->world-time)
                 :context/widgets (->world-widgets ctx))
          (merge (->world-map tiled-level))
          (spawn-creatures! tiled-level)))))

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
