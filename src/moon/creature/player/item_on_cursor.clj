(ns moon.creature.player.item-on-cursor
  (:require [component.core :refer [defc]]
            [gdx.graphics :as g]
            [gdx.input :refer [button-just-pressed?]]
            [gdx.math.vector :as v]
            [gdx.ui.stage-screen :refer [mouse-on-actor?]]
            [moon.item :refer [valid-slot? stackable?]]
            [moon.widgets.inventory :refer [clicked-inventory-cell]]
            [world.core :as world]
            [world.entity :as entity]
            [world.entity.state :as state]))

(defn- clicked-cell [eid cell]
  (let [entity @eid
        inventory (:entity/inventory entity)
        item-in-cell (get-in inventory cell)
        item-on-cursor (:entity/item-on-cursor entity)]
    (cond
     ; PUT ITEM IN EMPTY CELL
     (and (not item-in-cell)
          (valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/set-item eid cell item-on-cursor]
      [:e/dissoc eid :entity/item-on-cursor]
      [:tx/event eid :dropped-item]]

     ; STACK ITEMS
     (and item-in-cell
          (stackable? item-in-cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item eid cell item-on-cursor]
      [:e/dissoc eid :entity/item-on-cursor]
      [:tx/event eid :dropped-item]]

     ; SWAP ITEMS
     (and item-in-cell
          (valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/remove-item eid cell]
      [:tx/set-item eid cell item-on-cursor]
      ; need to dissoc and drop otherwise state enter does not trigger picking it up again
      ; TODO? coud handle pickup-item from item-on-cursor state also
      [:e/dissoc eid :entity/item-on-cursor]
      [:tx/event eid :dropped-item]
      [:tx/event eid :pickup-item item-in-cell]])))

; It is possible to put items out of sight, losing them.
; Because line of sight checks center of entity only, not corners
; this is okay, you have thrown the item over a hill, thats possible.

(defn- placement-point [player target maxrange]
  (v/add player
         (v/scale (v/direction player target)
                  (min maxrange
                       (v/distance player target)))))

(defn- item-place-position [entity]
  (placement-point (:position entity)
                   (g/world-mouse-position)
                   ; so you cannot put it out of your own reach
                   (- (:entity/click-distance-tiles entity) 0.1)))

(defn- world-item? []
  (not (mouse-on-actor?)))

(defc :player-item-on-cursor
  {:let {:keys [eid item]}}
  (entity/->v [[_ eid item]]
    {:eid eid
     :item item})

  (state/pause-game? [_]
    true)

  (state/manual-tick [_]
    (when (and (button-just-pressed? :left)
               (world-item?))
      [[:tx/event eid :drop-item]]))

  (clicked-inventory-cell [_ cell]
    (clicked-cell eid cell))

  (state/enter [_]
    [[:tx/cursor :cursors/hand-grab]
     [:e/assoc eid :entity/item-on-cursor item]])

  (state/exit [_]
    ; at clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (let [entity @eid]
      (when (:entity/item-on-cursor entity)
        [[:tx/sound "sounds/bfxr_itemputground.wav"]
         [:tx/item (item-place-position entity) (:entity/item-on-cursor entity)]
         [:e/dissoc eid :entity/item-on-cursor]])))

  (entity/render-below [_ entity]
    (when (world-item?)
      (g/draw-centered-image (:entity/image item) (item-place-position entity)))))

(defn draw-item-on-cursor []
  (let [player-e* @world/player]
    (when (and (= :player-item-on-cursor (state/state-k player-e*))
               (not (world-item?)))
      (g/draw-centered-image (:entity/image (:entity/item-on-cursor player-e*))
                             (g/gui-mouse-position)))))
