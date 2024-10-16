(ns world.setup
  (:require [clojure.gdx.tiled :as t]
            [clojure.gdx.utils :refer [dispose!]]
            [core.component :refer [defc]]
            [core.tx :as tx]
            [utils.core :refer [bind-root tile->middle]]
            [world.content-grid :as content-grid]
            [world.core :as world]
            [world.entity :as entity]
            world.generate
            [world.grid :as grid]
            world.widgets))

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

(defn spawn-creatures! [tiled-map start-position]
  (tx/do-all (for [creature (cons (world->player-creature start-position)
                                  (when spawn-enemies?
                                    (world->enemy-creatures tiled-map)))]
               [:tx/creature (update creature :position tile->middle)])))

(defn- init-new-world! [{:keys [tiled-map start-position]}]
  (bind-root #'world/entity-tick-error nil)
  (world/init-time!)
  (world/init-widget-data! (world.widgets/reset-stage!))
  (entity/init-uids-entities!)

  (when (bound? #'world/tiled-map)
    (dispose! world/tiled-map))
  (bind-root #'world/tiled-map tiled-map)

  (let [w (t/width  tiled-map)
        h (t/height tiled-map)
        grid (world.grid/init! w h (world-grid-position->value-fn tiled-map))]
    (world/init-raycaster! grid grid/blocks-vision?)
    (world/init-content-grid! {:cell-size 16 :width w :height h})
    (world/init-explored-tile-corners! w h))

  (spawn-creatures! tiled-map start-position))

; TODO  add-to-world/assoc/dissoc uid from entity move together here
; also core.screens/world ....
(defn add-world-ctx! [world-property-id]
  (init-new-world! (world.generate/generate-level world-property-id)))

(defc :tx/add-to-world
  (tx/do! [[_ eid]]
    (world/add-entity! eid)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @eid)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid/add-entity! eid)
    nil))

(defc :tx/remove-from-world
  (tx/do! [[_ eid]]
    (world/remove-entity! eid)
    (grid/remove-entity! eid)
    nil))

(defc :tx/position-changed
  (tx/do! [[_ eid]]
    (world/update-entity! eid)
    (grid/entity-position-changed! eid)
    nil))
