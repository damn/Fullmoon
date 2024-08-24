(ns components.entity.inventory
  (:require [utils.core :refer [find-first]]
            [core.component :refer [defcomponent]]
            [core.context :refer [get-property]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.inventory :as inventory]))

(defn- set-item [{:keys [entity/id] :as entity*} cell item]
  (let [inventory (:entity/inventory entity*)]
    (assert (and (nil? (get-in inventory cell))
                 (inventory/valid-slot? cell item))))
  [[:tx.entity/assoc-in id (cons :entity/inventory cell) item]
   (when (inventory/applies-modifiers? cell)
     [:tx/apply-modifiers id (:item/modifiers item)])
   (when (:entity/player? entity*)
     [:tx/set-item-image-in-widget cell item])])

(defn- remove-item [{:keys [entity/id] :as entity*} cell]
  (let [item (get-in (:entity/inventory entity*) cell)]
    (assert item)
    [[:tx.entity/assoc-in id (cons :entity/inventory cell) nil]
     (when (inventory/applies-modifiers? cell)
       [:tx/reverse-modifiers id (:item/modifiers item)])
     (when (:entity/player? entity*)
       [:tx/remove-item-from-widget cell])]))

(defcomponent :tx/set-item
  (effect/do! [[_ entity cell item] _ctx]
    (set-item @entity cell item)))

(defcomponent :tx/remove-item
  (effect/do! [[_ entity cell] _ctx]
    (remove-item @entity cell)))

; TODO doesnt exist, stackable, usable items with action/skillbar thingy
#_(defn remove-one-item [entity cell]
  (let [item (get-in (:entity/inventory @entity) cell)]
    (if (and (:count item)
             (> (:count item) 1))
      (do
       ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
       ; first remove and then place, just update directly  item ...
       (remove-item! context entity cell)
       (set-item! context entity cell (update item :count dec)))
      (remove-item! context entity cell))))

; TODO no items which stack are available
(defn- stack-item [entity* cell item]
  (let [cell-item (get-in (:entity/inventory entity*) cell)]
    (assert (inventory/stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item entity* cell)
            (set-item entity* cell (update cell-item :count + (:count item))))))

(defcomponent :tx/stack-item
  (effect/do! [[_ entity cell item] _ctx]
    (stack-item @entity cell item)))

(defn- try-put-item-in [entity* slot item]
  (let [inventory (:entity/inventory entity*)
        cells-items (inventory/cells-and-items inventory slot)
        [cell cell-item] (find-first (fn [[cell cell-item]] (inventory/stackable? item cell-item))
                                     cells-items)]
    (if cell
      (stack-item entity* cell item)
      (when-let [[empty-cell] (find-first (fn [[cell item]] (nil? item))
                                          cells-items)]
        (set-item entity* empty-cell item)))))

(defn- pickup-item [entity* item]
  (or
   (try-put-item-in entity* (:item/slot item)   item)
   (try-put-item-in entity* :inventory.slot/bag item)))

(defcomponent :tx/pickup-item
  (effect/do! [[_ entity item] _ctx]
    (pickup-item @entity item)))

(extend-type core.entity.Entity
  entity/Inventory
  (can-pickup-item? [entity* item]
    (boolean (pickup-item entity* item))))

(defcomponent :entity/inventory
  {:schema [:one-to-many-ids :properties/item]
   :let item-ids}
  (entity/create [_ eid context]
    (cons [:tx.entity/assoc eid :entity/inventory inventory/empty-inventory]
          (for [item-id item-ids]
            [:tx/pickup-item eid (get-property context item-id)]))))
