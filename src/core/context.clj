(ns core.context
  (:import com.badlogic.gdx.scenes.scene2d.Stage))

(defrecord Context [])

; direct data acess? or :timer ?
(defprotocol Time
  (update-time [_])
  (delta-time [_] "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed.")
  (elapsed-time [_] "The elapsed in-game-time (not counting when game is paused).")
  (logic-frame [_] "The game-logic frame number, starting with 1. (not counting when game is paused)")
  (->counter [_ duration])
  (stopped?       [_ counter])
  (reset          [_ counter])
  (finished-ratio [_ counter]))

; [entity.player :as player]
(defprotocol PlayerEntity
  (player-entity [_])
  (player-entity* [_])
  (player-update-state [_])
  (player-state-pause-game? [_])
  (player-clicked-inventory [_ cell])
  (player-clicked-skillmenu [_ skill]))

(defprotocol Graphics
  (render-gui-view   [_ render-fn] "render-fn is a function of param 'g', graphics context.")
  (render-world-view [_ render-fn] "render-fn is a function of param 'g', graphics context."))

(defprotocol Images
  (create-image [_ file])
  (get-sub-image [_ image [x y w h]])
  (spritesheet [_ file tilew tileh])
  (get-sprite [_ spritesheet [x y]] "x,y index starting top-left"))

(defprotocol Cursors
  (set-cursor! [_ cursor-key]))

(defprotocol Views
  (gui-mouse-position   [_])
  (gui-viewport-width   [_])
  (gui-viewport-height  [_])
  (world-mouse-position  [_])
  (world-camera [_])
  (world-viewport-width  [_])
  (world-viewport-height [_]))

(defprotocol StageScreen
  (->stage [_ actors] "Stage implements clojure.lang.ILookup (get) on actor id.")
  (get-stage ^Stage [_])
  (mouse-on-stage-actor? [_])
  (add-to-stage! [_ actor]))

(defprotocol TiledMapDrawer
  (render-tiled-map [_ tiled-map color-setter]
                    "Renders tiled-map using world-view at world-camera position and with world-unit-scale.
                    Color-setter is a gdl.ColorSetter which is called for every tile-corner to set the color.
                    Can be used for lights & shadows.
                    The map-renderers are created and cached internally.
                    Renders only visible layers."))

(defprotocol EffectHandler
  (do! [_ txs])
  (summarize-txs [_ txs])
  (frame->txs [_ frame-number]))

(defprotocol EntityComponentSystem
  (all-entities [_])
  (get-entity [_ uid])
  (tick-entities!   [_ entities*] "Calls tick system on all components of entities.")
  (render-entities! [_ g entities*] "Draws entities* in the correct z-order and in the order of render-systems for each z-order.")
  (remove-destroyed-entities! [_] "Calls destroy on all entities which are marked with ':tx/destroy'"))

(defprotocol MouseOverEntity
  (update-mouseover-entity [_])
  (mouseover-entity* [_]))

(defprotocol WorldRaycaster
  (ray-blocked?  [_ start target])
  (path-blocked? [_ start target path-w] "path-w in tiles. casts two rays."))

(defprotocol PotentialField
  (update-potential-fields [ctx entities])
  (potential-field-follow-to-enemy [_ entity]))

(defprotocol WorldLineOfSight
  (line-of-sight? [_ source* target*]))

(defprotocol ExploredTileCorners
  (explored? [_ position]))

(defprotocol World
  (start-new-game [_ lvl])
  (content-grid [_])
  (active-entities [_])
  (world-grid [_]))

(defprotocol PropertyStore
  (property [_ id])
  (all-properties [_ type])
  (update! [_ property])
  (delete! [_ id]))

(defprotocol BackgroundImage
  (->background-image [_]))

(defprotocol ActiveSkill
  (skill-usable-state [ctx entity* skill]))

; core.property.types.world ?
(defprotocol WorldGenerator
  (->world [ctx world-id]))

(defprotocol ErrorWindow
  (error-window! [_ throwable]))

(defprotocol PropertyEditor
  (->overview-table [_ property-type clicked-id-fn]
   "Creates a table with all-properties of property-type and buttons for each id which on-clicked calls clicked-id-fn."))
