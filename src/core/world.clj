(ns core.world
  (:require [clojure.ctx :refer :all]
            [clojure.gdx :refer :all :exclude [visible?]]
            [clojure.gdx.tiled :refer :all]
            [clojure.string :as str]
            [data.grid2d :as g]
            [core.entity :refer :all]
            )
  (:load "world/helper"
         "world/modules"
         "world/caves"
         "world/areas"
         "world/generators"
         "world/cached_renderer"
         "world/editor_screen"
         "world/potential_fields"
         "world/raycaster"
         "world/grid"
         "world/content_grid"
         "world/spawn"))

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
