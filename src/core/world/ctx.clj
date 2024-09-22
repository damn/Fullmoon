(ns core.world.ctx
  (:require [utils.core :refer [tile->middle]]
            [gdx.maps.tiled :as tiled]
            [core.component :refer [defcomponent] :as component]
            [core.ctx.content-grid :as content-grid]
            [core.ctx.grid :as grid]
            [core.entity.player :as player]
            [core.tx :as tx])
  (:import com.badlogic.gdx.utils.Disposable))

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
  (tx/do-all ctx
             (for [creature (cons (world->player-creature ctx tiled-level)
                                  (when spawn-enemies?
                                    (world->enemy-creatures ctx)))]
               [:tx/creature (update creature :position tile->middle)])))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
; done at module-gen? but not custom tiledmap?
(defn- ->world-map [{:keys [tiled-map start-position]}] ; == one object make ?! like graphics?
  ; grep context/grid -> all dependent stuff?
  (component/create-into {:context/tiled-map tiled-map
                          :context/start-position start-position}
                         {:context/grid [(tiled/width  tiled-map)
                                         (tiled/height tiled-map)
                                       #(case (tiled/movement-property tiled-map %)
                                          "none" :none
                                          "air"  :air
                                          "all"  :all)]
                          :context/raycaster grid/blocks-vision?
                          :context/content-grid [16 16]
                          :context/explored-tile-corners true}))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (let [ctx (dissoc ctx :context/entity-tick-error)
        ctx (-> ctx
                (merge {:context/game-loop-mode mode}
                       (component/create-into ctx
                                              {:context/ecs true
                                               :context/time true
                                               :context/widgets true
                                               :context/effect-handler [mode record-transactions?]})))]
    (case mode
      :game-loop/normal (do
                         (when-let [tiled-map (:context/tiled-map ctx)]
                           (.dispose ^Disposable tiled-map))
                         (-> ctx
                             (merge (->world-map tiled-level))
                             (spawn-creatures! tiled-level)))
      :game-loop/replay (merge ctx (->world-map (select-keys ctx [:context/tiled-map
                                                                  :context/start-position]))))))

(defn start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

(defn active-entities [ctx]
  (content-grid/active-entities ctx (player/entity* ctx)))

(defcomponent :tx/add-to-world
  (tx/do! [[_ entity] ctx]
    (content-grid/update-entity! ctx entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid/add-entity! ctx entity)
    ctx))

(defcomponent :tx/remove-from-world
  (tx/do! [[_ entity] ctx]
    (content-grid/remove-entity! ctx entity)
    (grid/remove-entity! ctx entity)
    ctx))

(defcomponent :tx/position-changed
  (tx/do! [[_ entity] ctx]
    (content-grid/update-entity! ctx entity)
    (grid/entity-position-changed! ctx entity)
    ctx))
