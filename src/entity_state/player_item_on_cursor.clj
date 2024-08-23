(ns entity-state.player-item-on-cursor
  (:require [gdx.input :as input]
            [math.vector :as v]
            [api.context :as ctx :refer [mouse-on-stage-actor?]]
            [api.graphics :as g]
            [gdx.input.buttons :as buttons]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.inventory :as inventory]))

(defn- clicked-cell [{:keys [entity/id] :as entity*} cell]
  (let [inventory (:entity/inventory entity*)
        item (get-in inventory cell)
        item-on-cursor (:entity/item-on-cursor entity*)]
    (cond
     ; PUT ITEM IN EMPTY CELL
     (and (not item)
          (inventory/valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/set-item id cell item-on-cursor]
      [:tx.entity/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; STACK ITEMS
     (and item (inventory/stackable? item item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item id cell item-on-cursor]
      [:tx.entity/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; SWAP ITEMS
     (and item
          (inventory/valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/remove-item id cell]
      [:tx/set-item id cell item-on-cursor]
      ; need to dissoc and drop otherwise state enter does not trigger picking it up again
      ; TODO? coud handle pickup-item from item-on-cursor state also
      [:tx.entity/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]
      [:tx/event id :pickup-item item]])))

; It is possible to put items out of sight, losing them.
; Because line of sight checks center of entity only, not corners
; this is okay, you have thrown the item over a hill, thats possible.

(defn- placement-point [player target maxrange]
  (v/add player
         (v/scale (v/direction player target)
                  (min maxrange
                       (v/distance player target)))))

(defn- item-place-position [ctx entity*]
  (placement-point (:position entity*)
                   (ctx/world-mouse-position ctx)
                   ; so you cannot put it out of your own reach
                   (- (:entity/click-distance-tiles entity*) 0.1)))

(defn- world-item? [ctx]
  (not (mouse-on-stage-actor? ctx)))

(defrecord PlayerItemOnCursor [eid item]
  state/PlayerState
  (player-enter [_])
  (pause-game? [_] true)

  (manual-tick [_ context]
    (when (and (input/button-just-pressed? buttons/left)
               (world-item? context))
      [[:tx/event eid :drop-item]]))

  (clicked-inventory-cell [_ cell]
    (clicked-cell @eid cell))

  (clicked-skillmenu-skill [_ skill])

  state/State
  (enter [_ _ctx]
    [[:tx.context.cursor/set :cursors/hand-grab]
     [:tx.entity/assoc eid :entity/item-on-cursor item]])

  (exit [_ ctx]
    ; at context.ui.inventory-window/clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (let [entity* @eid]
      (when (:entity/item-on-cursor entity*)
        [[:tx/sound "sounds/bfxr_itemputground.wav"]
         [:tx.entity/item (item-place-position ctx entity*) (:entity/item-on-cursor entity*)]
         [:tx.entity/dissoc eid :entity/item-on-cursor]])))

  (tick [_ _ctx])

  (render-below [_ entity* g ctx]
    (when (world-item? ctx)
      (g/draw-centered-image g (:property/image item) (item-place-position ctx entity*))))
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn draw-item-on-cursor [g context]
  (let [player-entity* (ctx/player-entity* context)]
    (when (and (= :item-on-cursor (entity/state player-entity*))
               (not (world-item? context)))
      (g/draw-centered-image g
                             (:property/image (:entity/item-on-cursor player-entity*))
                             (ctx/gui-mouse-position context)))))

(defn ->build [ctx eid item]
  (->PlayerItemOnCursor eid item))
