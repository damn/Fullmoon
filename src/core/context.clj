(ns core.context
  (:import com.badlogic.gdx.scenes.scene2d.Stage))

(defrecord Context [])

(defprotocol Game
  (game-paused? [_]))

(defprotocol Time
  (update-time [_])
  (delta-time [_] "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed.")
  (elapsed-time [_] "The elapsed in-game-time (not counting when game is paused).")
  (logic-frame [_] "The game-logic frame number, starting with 1. (not counting when game is paused)")
  (->counter [_ duration])
  (stopped?       [_ counter])
  (reset          [_ counter])
  (finished-ratio [_ counter]))

(defprotocol PlayerEntity
  (player-entity [_])
  (player-entity* [_])
  (player-update-state [_])
  (player-state-pause-game? [_])
  (player-clicked-inventory [_ cell])
  (player-clicked-skillmenu [_ skill]))

(defprotocol ApplicationScreens
  (current-screen-key [_])
  (current-screen [_])
  (change-screen [_ new-screen]
                 "Calls screen/hide on the current-screen (if there is one).
                 Throws AssertionError when the context does not have a new-screen.
                 Calls screen/show on the new screen and
                 returns the context with current-screen set to new-screen.")
  (init-first-screen [_]))

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
  (world-viewport-height [_])
  (update-viewports [_ w h]))

(defprotocol StageScreen
  (->stage [_ actors])
  (get-stage ^Stage [_] "Stage implements clojure.lang.ILookup (get) on actor id.")
  (mouse-on-stage-actor? [_])
  (add-to-stage! [_ actor]))

(defprotocol Widgets
  (->actor [_ {:keys [draw act]}])
  (->group [_ {:keys [actors] :as opts}])
  (->text-button [_ text on-clicked])
  (->check-box [_ text on-clicked checked?] "on-clicked is a fn of one arg, taking the current isChecked state")
  (->select-box [_ {:keys [items selected]}])
  (->image-button [_ image on-clicked]
                  [_ image on-clicked {:keys [scale]}])
  (->table [_ opts])
  (->window [_ {:keys [title modal? close-button? center?] :as opts}])
  (->label [_ text])
  (->text-field [_ text opts])
  (->split-pane [_ {:keys [first-widget
                           second-widget
                           vertical?] :as opts}])
  (->stack [_ actors])
  (->image-widget [_ object opts] "Takes either an image or drawable. Opts are :scaling, :align and actor opts.")
  (->texture-region-drawable [_ texture-region])
  (->horizontal-group [_ {:keys [space pad]}])
  (->vertical-group [_ actors])
  (->button-group [_ {:keys [max-check-count min-check-count]}])
  (->scroll-pane [_ actor]))

(defprotocol TiledMapLoader
  (->tiled-map [_ file] "Needs to be disposed.")
  (render-tiled-map [_ tiled-map color-setter]
                    "Renders tiled-map using world-view at world-camera position and with world-unit-scale.
                    Color-setter is a gdl.ColorSetter which is called for every tile-corner to set the color.
                    Can be used for lights & shadows.
                    The map-renderers are created and cached internally.
                    Renders only visible layers."))

(defprotocol Assets
  (play-sound! [_ file] "Sound is already loaded from file, this will perform only a lookup for the sound and play it.")
  (cached-texture [_ file])
  (all-sound-files   [_])
  (all-texture-files [_]))

(defprotocol EffectHandler
  (do! [_ txs])
  (effect-applicable? [_ effects])
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

(defprotocol InventoryWindow
  (inventory-window [_]))

(defprotocol Actionbar
  (selected-skill [_]))

(defprotocol BackgroundImage
  (->background-image [_]))

(defprotocol ActiveSkill
  (skill-usable-state [ctx entity* skill]))

(defprotocol WorldGenerator
  (->world [ctx world-id]))

(defprotocol DebugRender
 (debug-render-before-entities [_ g])
 (debug-render-after-entities  [_ g]))

(defprotocol IngameWindows
  (check-window-hotkeys [_])
  (close-windows? [_]))

(defprotocol WorldTiledMap
  (render-map [_ light-position]))

(defprotocol ErrorWindow
  (error-window! [_ throwable]))

(defprotocol PropertyEditor
  (->overview-table [_ property-type clicked-id-fn]
   "Creates a table with all-properties of property-type and buttons for each id which on-clicked calls clicked-id-fn."))
