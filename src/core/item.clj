(ns core.item
  (:require [clojure.ctx :refer :all]
            [clojure.gdx :refer :all :exclude [->cursor set-cursor!]]
            [core.stat :refer [mod-info-text]]
            [data.grid2d :as grid2d])
  (:import com.badlogic.gdx.graphics.Color
           com.badlogic.gdx.scenes.scene2d.Actor
           (com.badlogic.gdx.scenes.scene2d.ui Widget Image Table)
           com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
           com.badlogic.gdx.scenes.scene2d.utils.ClickListener
           com.badlogic.gdx.math.Vector2))

(def ^:private empty-inventory
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
              [slot (grid2d/create-grid width height (constantly nil))]))
       (into {})))

(defcomponent :item/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (info-text [_ _ctx]
    (when (seq modifiers)
      (mod-info-text modifiers))))

(defcomponent :item/slot
  {:data [:enum (keys empty-inventory)]})

(def ^:private body-props
  {:width 0.75
   :height 0.75
   :z-order :z-order/on-ground})

(defcomponent :tx/item
  (do! [[_ position item] _ctx]
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

(defn- set-item [{:keys [entity/id] :as entity*} cell item]
  (let [inventory (:entity/inventory entity*)]
    (assert (and (nil? (get-in inventory cell))
                 (valid-slot? cell item))))
  [[:e/assoc-in id (cons :entity/inventory cell) item]
   (when (applies-modifiers? cell)
     [:tx/apply-modifiers id (:item/modifiers item)])
   (when (:entity/player? entity*)
     [:tx/set-item-image-in-widget cell item])])

(defn- remove-item [{:keys [entity/id] :as entity*} cell]
  (let [item (get-in (:entity/inventory entity*) cell)]
    (assert item)
    [[:e/assoc-in id (cons :entity/inventory cell) nil]
     (when (applies-modifiers? cell)
       [:tx/reverse-modifiers id (:item/modifiers item)])
     (when (:entity/player? entity*)
       [:tx/remove-item-from-widget cell])]))

(defcomponent :tx/set-item
  (do! [[_ entity cell item] _ctx]
    (set-item @entity cell item)))

(defcomponent :tx/remove-item
  (do! [[_ entity cell] _ctx]
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
    (assert (stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item entity* cell)
            (set-item entity* cell (update cell-item :count + (:count item))))))

(defcomponent :tx/stack-item
  (do! [[_ entity cell item] _ctx]
    (stack-item @entity cell item)))

(defn- try-put-item-in [entity* slot item]
  (let [inventory (:entity/inventory entity*)
        cells-items (cells-and-items inventory slot)
        [cell _cell-item] (find-first (fn [[_cell cell-item]] (stackable? item cell-item))
                                      cells-items)]
    (if cell
      (stack-item entity* cell item)
      (when-let [[empty-cell] (find-first (fn [[_cell item]] (nil? item))
                                          cells-items)]
        (set-item entity* empty-cell item)))))

(defn- pickup-item [entity* item]
  (or
   (try-put-item-in entity* (:item/slot item)   item)
   (try-put-item-in entity* :inventory.slot/bag item)))

(defcomponent :tx/pickup-item
  (do! [[_ entity item] _ctx]
    (pickup-item @entity item)))

(extend-type clojure.ctx.Entity
  Inventory
  (can-pickup-item? [entity* item]
    (boolean (pickup-item entity* item))))

(defcomponent :entity/inventory
  {:data [:one-to-many :properties/items]}
  (create [[_ items] eid _ctx]
    (cons [:e/assoc eid :entity/inventory empty-inventory]
          (for [item items]
            [:tx/pickup-item eid item]))))


; Items are also smaller than 48x48 all of them
; so wasting space ...
; can maybe make a smaller textureatlas or something...

(def ^:private cell-size 48)
(def ^:private droppable-color    [0   0.6 0 0.8])
(def ^:private not-allowed-color  [0.6 0   0 0.8])

(defn- draw-cell-rect [g player-entity* x y mouseover? cell]
  (draw-rectangle g x y cell-size cell-size Color/GRAY)
  (when (and mouseover?
             (= :player-item-on-cursor (entity-state player-entity*)))
    (let [item (:entity/item-on-cursor player-entity*)
          color (if (valid-slot? cell item)
                  droppable-color
                  not-allowed-color)]
      (draw-filled-rectangle g (inc x) (inc y) (- cell-size 2) (- cell-size 2) color))))

(defn- mouseover? [^Actor actor [x y]]
  (let [v (.stageToLocalCoordinates actor (Vector2. x y))]
    (.hit actor (.x v) (.y v) true)))

; TODO why do I need to call getX ?
; is not layouted automatically to cell , use 0/0 ??
; (maybe (.setTransform stack true) ? , but docs say it should work anyway
(defn- draw-rect-actor ^Widget []
  (proxy [Widget] []
    (draw [_batch _parent-alpha]
      (let [{g :context/graphics :as ctx} @app-state
            g (assoc g :unit-scale 1)
            player-entity* (player-entity* ctx)
            ^Widget this this]
        (draw-cell-rect g
                        player-entity*
                        (.getX this)
                        (.getY this)
                        (mouseover? this (gui-mouse-position ctx))
                        (actor-id (parent this)))))))

(defn- ->cell [slot->background slot & {:keys [position]}]
  (let [cell [slot (or position [0 0])]
        image-widget (->image-widget (slot->background slot) {:id :image})
        stack (->stack [(draw-rect-actor) image-widget])]
    (set-name! stack "inventory-cell")
    (set-id! stack cell)
    (add-listener! stack (proxy [ClickListener] []
                                 (clicked [event x y]
                                   (swap! app-state #(effect! % (player-clicked-inventory % cell))))))
    stack))

(defn- slot->background [ctx]
  (let [sheet (sprite-sheet ctx "images/items.png" 48 48)]
    (->> #:inventory.slot {:weapon   0
                           :shield   1
                           :rings    2
                           :necklace 3
                           :helm     4
                           :cloak    5
                           :chest    6
                           :leg      7
                           :glove    8
                           :boot     9
                           :bag      10} ; transparent
         (map (fn [[slot y]]
                (let [drawable (->texture-region-drawable (:texture-region (sprite ctx sheet [21 (+ y 2)])))]
                  (.setMinSize drawable (float cell-size) (float cell-size))
                  [slot
                   (.tint ^TextureRegionDrawable drawable (->color 1 1 1 0.4))])))
         (into {}))))

; TODO move together with empty-inventory definition ?
(defn- redo-table! [^Table table slot->background]
  ; cannot do add-rows, need bag :position idx
  (let [cell (fn [& args] (apply ->cell slot->background args))] ; TODO cell just return type hint ^Actor
    (.clear table) ; no need as we create new table ... TODO
    (doto table .add .add
      (.add ^Actor (cell :inventory.slot/helm))
      (.add ^Actor (cell :inventory.slot/necklace)) .row)
    (doto table .add
      (.add ^Actor (cell :inventory.slot/weapon))
      (.add ^Actor (cell :inventory.slot/chest))
      (.add ^Actor (cell :inventory.slot/cloak))
      (.add ^Actor (cell :inventory.slot/shield)) .row)
    (doto table .add .add
      (.add ^Actor (cell :inventory.slot/leg)) .row)
    (doto table .add
      (.add ^Actor (cell :inventory.slot/glove))
      (.add ^Actor (cell :inventory.slot/rings :position [0 0]))
      (.add ^Actor (cell :inventory.slot/rings :position [1 0]))
      (.add ^Actor (cell :inventory.slot/boot)) .row)
    ; TODO add separator
    (doseq [y (range (grid2d/height (:inventory.slot/bag empty-inventory)))]
      (doseq [x (range (grid2d/width (:inventory.slot/bag empty-inventory)))]
        (.add table ^Actor (cell :inventory.slot/bag :position [x y])))
      (.row table))))

(defn ->build [ctx {:keys [slot->background]}]
  (let [table (->table {:id ::table})]
    (redo-table! table slot->background)
    (->window {:title "Inventory"
                  :id :inventory-window
                  :visible? false
                  :pack? true
                  :position [(gui-viewport-width ctx)
                             (gui-viewport-height ctx)]
                  :rows [[{:actor table :pad 4}]]})))

(defn ->data [ctx]
  (slot->background ctx))

(defn- get-inventory [ctx]
  {:table (::table (get (:windows (stage-get ctx)) :inventory-window))
   :slot->background (:slot->background (:context/widgets ctx))})

(defcomponent :tx/set-item-image-in-widget
  (do! [[_ cell item] ctx]
    (let [{:keys [table]} (get-inventory ctx)
          cell-widget (get table cell)
          ^Image image-widget (get cell-widget :image)
          drawable (->texture-region-drawable (:texture-region (:entity/image item)))]
      (.setMinSize drawable (float cell-size) (float cell-size))
      (.setDrawable image-widget drawable)
      (add-tooltip! cell-widget #(->info-text item %))
      ctx)))

(defcomponent :tx/remove-item-from-widget
  (do! [[_ cell] ctx]
    (let [{:keys [table slot->background]} (get-inventory ctx)
          cell-widget (get table cell)
          ^Image image-widget (get cell-widget :image)]
      (.setDrawable image-widget (slot->background (cell 0)))
      (remove-tooltip! cell-widget)
      ctx)))
