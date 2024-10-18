(ns moon.item
  (:require [component.core :refer [defc]]
            [component.info :as info]
            [component.property :as property]
            [component.tx :as tx]
            [data.grid2d :as g2d]
            [utils.core :refer [find-first]]
            [world.entity :as entity]
            [world.entity.modifiers :refer [mod-info-text]]))

(def empty-inventory
  (->> #:inventory.slot{:bag      [6 4]
                        :weapon   [1 1]
                        :shield   [1 1]
                        :helm     [1 1]
                        :chest    [1 1]
                        :leg      [1 1]
                        :glove    [1 1]
                        :boot     [1 1]
                        :cloak    [1 1]
                        :necklace [1 1]
                        :rings    [2 1]}
       (map (fn [[slot [width height]]]
              [slot (g2d/create-grid width height (constantly nil))]))
       (into {})))

(defc :item/slot
  {:schema (apply vector :enum (keys empty-inventory))})

(defc :item/modifiers
  {:schema [:s/components-ns :modifier]
   :let modifiers}
  (info/text [_]
    (when (seq modifiers)
      (mod-info-text modifiers))))

(property/def :properties/items
  {:schema [:property/pretty-name
            :entity/image
            :item/slot
            [:item/modifiers {:optional true}]]
   :overview {:title "Items"
              :columns 20
              :image/scale 1.1
              :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                     (name slot)
                                     "")
                             (name (:property/id %)))}})

(def ^:private body-props
  {:width 0.75
   :height 0.75
   :z-order :z-order/on-ground})

(defc :tx/item
  (tx/do! [[_ position item]]
    [[:e/create position body-props {:entity/image (:entity/image item)
                                     :entity/item item
                                     :entity/clickable {:type :clickable/item
                                                        :text (:property/pretty-name item)}}]]))

(defn- cells-and-items [inventory slot]
  (for [[position item] (slot inventory)]
    [[slot position] item]))

(defn valid-slot? [[slot _] item]
  (or (= :inventory.slot/bag slot)
      (= (:item/slot item) slot)))

(defn- applies-modifiers? [[slot _]]
  (not= :inventory.slot/bag slot))

(defn stackable? [item-a item-b]
  (and (:count item-a)
       (:count item-b) ; this is not required but can be asserted, all of one name should have count if others have count
       (= (:property/id item-a) (:property/id item-b))))

(defn- set-item [eid cell item]
  (let [entity @eid
        inventory (:entity/inventory entity)]
    (assert (and (nil? (get-in inventory cell))
                 (valid-slot? cell item)))
    [[:e/assoc-in eid (cons :entity/inventory cell) item]
     (when (applies-modifiers? cell)
       [:tx/apply-modifiers eid (:item/modifiers item)])
     (when (:entity/player? entity)
       [:tx/set-item-image-in-widget cell item])]))

(defn- remove-item [eid cell]
  (let [entity @eid
        item (get-in (:entity/inventory entity) cell)]
    (assert item)
    [[:e/assoc-in eid (cons :entity/inventory cell) nil]
     (when (applies-modifiers? cell)
       [:tx/reverse-modifiers eid (:item/modifiers item)])
     (when (:entity/player? entity)
       [:tx/remove-item-from-widget cell])]))

(defc :tx/set-item
  (tx/do! [[_ eid cell item]]
    (set-item eid cell item)))

(defc :tx/remove-item
  (tx/do! [[_ eid cell]]
    (remove-item eid cell)))

; TODO doesnt exist, stackable, usable items with action/skillbar thingy
#_(defn remove-one-item [eid cell]
  (let [item (get-in (:entity/inventory @eid) cell)]
    (if (and (:count item)
             (> (:count item) 1))
      (do
       ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
       ; first remove and then place, just update directly  item ...
       (remove-item! eid cell)
       (set-item! eid cell (update item :count dec)))
      (remove-item! eid cell))))

; TODO no items which stack are available
(defn- stack-item [eid cell item]
  (let [cell-item (get-in (:entity/inventory @eid) cell)]
    (assert (stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item eid cell)
            (set-item eid cell (update cell-item :count + (:count item))))))

(defc :tx/stack-item
  (tx/do! [[_ eid cell item]]
    (stack-item eid cell item)))

(defn- try-put-item-in [eid slot item]
  (let [inventory (:entity/inventory @eid)
        cells-items (cells-and-items inventory slot)
        [cell _cell-item] (find-first (fn [[_cell cell-item]] (stackable? item cell-item))
                                      cells-items)]
    (if cell
      (stack-item eid cell item)
      (when-let [[empty-cell] (find-first (fn [[_cell item]] (nil? item))
                                          cells-items)]
        (set-item eid empty-cell item)))))

(defn- pickup-item [eid item]
  (or
   (try-put-item-in eid (:item/slot item)   item)
   (try-put-item-in eid :inventory.slot/bag item)))

(defc :tx/pickup-item
  (tx/do! [[_ eid item]]
    (pickup-item eid item)))

(defn can-pickup-item? [eid item]
  (boolean (pickup-item eid item)))

(defc :entity/inventory
  {:schema [:s/one-to-many :properties/items]}
  (entity/create [[_ items] eid]
    (cons [:e/assoc eid :entity/inventory empty-inventory]
          (for [item items]
            [:tx/pickup-item eid item]))))
