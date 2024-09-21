(ns core.context
  (:import com.badlogic.gdx.scenes.scene2d.Stage))

(defrecord Context [])

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

(defprotocol MouseOverEntity
  (update-mouseover-entity [_])
  (mouseover-entity* [_]))

(defprotocol WorldRaycaster
  (ray-blocked?  [_ start target])
  (path-blocked? [_ start target path-w] "path-w in tiles. casts two rays."))

(defprotocol WorldLineOfSight
  (line-of-sight? [_ source* target*]))

(defprotocol ExploredTileCorners
  (explored? [_ position]))

(defprotocol World
  (active-entities [_])
  (world-grid [_]))

(defprotocol BackgroundImage
  (->background-image [_]))

; skills & effects together = 'core.action' ?
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
