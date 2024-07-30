(ns cdq.state.player-item-on-cursor
  (:require [gdl.context :as ctx :refer [mouse-on-stage-actor? button-just-pressed?]]
            [gdl.graphics :as g]
            [gdl.input.buttons :as buttons]
            [gdl.math.vector :as v]
            [cdq.api.context :refer [item-entity]]
            [cdq.api.entity :as entity]
            [cdq.entity.inventory :as inventory]
            [cdq.api.state :as state]))

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
      [:tx/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; STACK ITEMS
     (and item (inventory/stackable? item item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item id cell item-on-cursor]
      [:tx/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; SWAP ITEMS
     (and item
          (inventory/valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/remove-item id cell]
      [:tx/set-item id cell item-on-cursor]
      ; need to dissoc and drop otherwise state enter does not trigger picking it up again
      ; TODO? coud handle pickup-item from item-on-cursor state also
      [:tx/dissoc id :entity/item-on-cursor]
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
  (placement-point (:entity/position entity*)
                   (ctx/world-mouse-position ctx)
                   ; so you cannot put it out of your own reach
                   (- (:entity/click-distance-tiles entity*) 0.1)))

(defn- world-item? [ctx]
  (not (mouse-on-stage-actor? ctx)))

(defrecord PlayerItemOnCursor [item]
  state/PlayerState
  (player-enter [_])
  (pause-game? [_] true)
  (manual-tick [_ entity* context]
    (when (and (button-just-pressed? context buttons/left)
               (world-item? context))
      [[:tx/event (:entity/id entity*) :drop-item]]))
  (clicked-inventory-cell [_ entity* cell]
    (clicked-cell entity* cell))
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ {:keys [entity/id]} _ctx]
    [[:tx/cursor :cursors/hand-grab]
     [:tx/assoc id :entity/item-on-cursor item]])

  (exit [_ {:keys [entity/id] :as entity*} ctx]
    ; at context.ui.inventory-window/clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (when (:entity/item-on-cursor entity*)
      [[:tx/sound "sounds/bfxr_itemputground.wav"]
       [:tx/create (item-entity ctx
                                (item-place-position ctx entity*)
                                (:entity/item-on-cursor entity*))]
       [:tx/dissoc id :entity/item-on-cursor]]))

  (tick [_ entity* _ctx])
  (render-below [_ entity* g ctx]
    (when (world-item? ctx)
      (g/draw-centered-image g (:property/image item) (item-place-position ctx entity*))))
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn draw-item-on-cursor [{:keys [context/player-entity] g :gdl.libgdx.context/graphics :as context}]
  (when (and (= :item-on-cursor (entity/state @player-entity))
             (not (world-item? context)))
    (g/draw-centered-image g
                           (:property/image (:entity/item-on-cursor @player-entity))
                           (ctx/gui-mouse-position context))))
