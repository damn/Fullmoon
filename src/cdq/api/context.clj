(ns cdq.api.context
  (:require [core.component :as component]))

(component/defn transact! [_ ctx])

(defprotocol TransactionHandler
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
  (of-type? [_ property-type property]
            "Returns true if the property is of that type.")
  (validate [_ property {:keys [humanize?]}]
            "If property is valid as of defined types.")
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
  (inventory-window [_])
  (rebuild-inventory-widgets [_]))

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
  (reset-actionbar [_])
  (selected-skill  [_]))

(defprotocol DebugRender ; TODO move out of context api ....
  (debug-render-before-entities [_ g])
  (debug-render-after-entities  [_ g]))

(defprotocol Cursor
  (set-cursor! [_ cursor-key]))

(defprotocol TooltipText
  (tooltip-text [_ property])
  (player-tooltip-text [_ property]))

(defprotocol ErrorModal
  (->error-window [_ throwable]))

(defprotocol Windows
  (windows [_])
  (id->window [_ window-id]))

(defprotocol BackgroundImage
  (->background-image [_]))
