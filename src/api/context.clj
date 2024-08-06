(ns api.context
  (:require [core.component :refer [defsystem]]))

(defrecord Context [])

(defsystem render [_ ctx])

(defprotocol Game
  (start-new-game [_ tiled-level])
  (render-game [_]))

(defprotocol Application
  (exit-app [_]))

(defprotocol ApplicationScreens
  (current-screen [_])
  (change-screen [_ new-screen]
                 "Calls screen/hide on the current-screen (if there is one).
                 Throws AssertionError when the context does not have a new-screen.
                 Calls screen/show on the new screen and
                 returns the context with current-screen set to new-screen."))

(defprotocol Graphics
  (delta-time [_] "the time span between the current frame and the last frame in seconds.")
  (frames-per-second [_] "the average number of frames per second")

  (gui-mouse-position   [_])
  (gui-viewport-width   [_])
  (gui-viewport-height  [_])

  (world-mouse-position  [_])
  (world-camera          [_])
  (world-viewport-width  [_])
  (world-viewport-height [_])

  (->cursor [_ file hotspot-x hotspot-y] "Needs to be disposed.")
  (set-cursor! [_ cursor])

  (->color [_ r g b a])

  (create-image [_ file])
  (get-sub-image [_ image [x y w h]])
  (spritesheet [_ file tilew tileh])
  (get-sprite [_ spritesheet [x y]] "x,y index starting top-left"))

(defprotocol Input
  (button-pressed?      [_ button])
  (button-just-pressed? [_ button])
  (key-pressed?      [_ k])
  (key-just-pressed? [_ k]))

(defprotocol Stage
  (->stage-screen [_ {:keys [stage sub-screen]}]
                  "A screen with a stage as an input-processor which gets drawn and 'act'ed after the given sub-screen.
                  The stage will get disposed also.
                  Sub-screen is optional.")
  (get-stage [_] "Stage implements clojure.lang.ILookup (get) on actor id.")
  (mouse-on-stage-actor? [_])
  (add-to-stage! [_ actor]))

(defprotocol Widgets
  (->actor [_ {:keys [draw act]}])
  (->group [_ {:keys [actors] :as opts}])
  (->text-button [_ text on-clicked])
  (->check-box [_ text on-clicked checked?] "on-clicked is a fn of one arg, taking the current isChecked state")
  (->select-box [_ {:keys [items selected]}])
  (->image-button [_ image on-clicked]
                  [_ image on-clicked {:keys [dimensions]}])
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
  (->tiled-map [_ file] "Needs to be disposed."))

(defprotocol Assets
  (play-sound! [_ file] "Sound is already loaded from file, this will perform only a lookup for the sound and play it.")
  (cached-texture [_ file])
  (all-sound-files   [_])
  (all-texture-files [_]))

(defprotocol TransactionHandler
  (set-record-txs! [_ bool])
  (clear-recorded-txs! [_])
  (summarize-txs [_ txs])
  (transact-all! [_ txs])
  (frame->txs [_ frame-number]))

(defprotocol EntityComponentSystem
  (all-entities [_])
  (get-entity [_ uid])
  (tick-entities!   [_ entities*] "Calls tick system on all components of entities.")
  (render-entities! [_ g entities*] "Draws entities* in the correct z-order and in the order of render-systems for each z-order.")
  (remove-destroyed-entities! [_] "Calls destroy on all entities which are marked with ':tx/destroy'"))

(defprotocol MouseOverEntity
  (update-mouseover-entity! [_]))

(defprotocol World
  (render-map [_])
  (line-of-sight? [_ source* target*])
  (ray-blocked?  [_ start target])
  (path-blocked? [_ start target path-w] "path-w in tiles. casts two rays.")
  ; TODO explored-grid
  (explored?     [_ position])
  (content-grid [_])
  (world-grid [_]))

(defprotocol EffectInterpreter
  (effect-text        [_ effect])
  (valid-params?      [_ effect])
  (effect-render-info [_ g effect])
  (effect-useful?     [_ effect]))

(defprotocol Modifier
  (modifier-text [_ modifier]))

(defprotocol Builder
  ; TODO ?
  (item-entity [_ position item])
  (line-entity [_ {:keys [start end duration color thick?]}]))

; TODO get from world?
(defprotocol PotentialField
  (update-potential-fields! [_ entities])
  (potential-field-follow-to-enemy [_ entity]))

(defprotocol PropertyTypes
  (of-type? [_ property type] "Returns true if the property is of that type.")
  (validate [_ property {:keys [humanize?]}] "If property is valid as of defined types.")
  (property->type [_ property])
  (edn-file-sort-order [_ property-type])
  (overview [_ property-type])
  (property-types [_]))

(defprotocol PropertyStore
  (get-property [_ id])
  (all-properties [_ type])
  (update! [_ property])
  (delete! [_ id]))

(defprotocol InventoryWindow
  (inventory-window [_]))

(defprotocol Counter
  (->counter [_ duration])
  (stopped?       [_ counter])
  (reset          [_ counter])
  (finished-ratio [_ counter])
  (update-elapsed-game-time! [_]))

(defprotocol Skills
  (skill-usable-state [effect-context entity* skill]))

(defprotocol Actionbar
  (->action-bar    [_])
  (selected-skill  [_]))

(defprotocol Cursor
  (set-cursork! [_ cursor-key]))

(defprotocol TooltipText
  (tooltip-text [_ property])
  (player-tooltip-text [_ property]))
