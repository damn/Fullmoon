(ns components.widgets.inventory
  (:require [gdx.graphics :as graphics]
            [gdx.graphics.color :as color]
            [data.grid2d :as grid]
            [core.component :as component :refer [defcomponent]]
            [core.components :as components]
            [core.context :as ctx :refer [spritesheet get-sprite ->table ->window ->texture-region-drawable ->stack ->image-widget]]
            [core.graphics :as g]
            [core.scene2d.actor :as actor :refer [set-id! add-listener! set-name! add-tooltip! remove-tooltip!]]
            [core.entity :as entity]
            [core.inventory :as inventory]
            app)
  (:import com.badlogic.gdx.scenes.scene2d.Actor
           (com.badlogic.gdx.scenes.scene2d.ui Widget Image Table)
           com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
           com.badlogic.gdx.scenes.scene2d.utils.ClickListener
           com.badlogic.gdx.math.Vector2))

; Items are also smaller than 48x48 all of them
; so wasting space ...
; can maybe make a smaller textureatlas or something...

(def ^:private cell-size 48)
(def ^:private droppable-color    [0   0.6 0 0.8])
(def ^:private not-allowed-color  [0.6 0   0 0.8])

(defn- draw-cell-rect [g player-entity* x y mouseover? cell]
  (g/draw-rectangle g x y cell-size cell-size color/gray)
  (when (and mouseover?
             (= :player-item-on-cursor (entity/state player-entity*)))
    (let [item (:entity/item-on-cursor player-entity*)
          color (if (inventory/valid-slot? cell item)
                 droppable-color
                 not-allowed-color)]
      (g/draw-filled-rectangle g (inc x) (inc y) (- cell-size 2) (- cell-size 2) color))))

(defn- mouseover? [^Actor actor [x y]]
  (let [v (.stageToLocalCoordinates actor (Vector2. x y))]
    (.hit actor (.x v) (.y v) true)))

; TODO why do I need to call getX ?
; is not layouted automatically to cell , use 0/0 ??
; (maybe (.setTransform stack true) ? , but docs say it should work anyway
(defn- draw-rect-actor ^Widget []
  (proxy [Widget] []
    (draw [_batch _parent-alpha]
      (let [{g :context/graphics :as ctx} @app/state
            g (assoc g :unit-scale 1)
            player-entity* (ctx/player-entity* ctx)
            ^Widget this this]
        (draw-cell-rect g
                        player-entity*
                        (.getX this)
                        (.getY this)
                        (mouseover? this (ctx/gui-mouse-position ctx))
                        (actor/id (actor/parent this)))))))

(defn- ->cell [{:keys [context/state] :as ctx} slot->background slot & {:keys [position]}]
  (let [cell [slot (or position [0 0])]
        image-widget (->image-widget ctx (slot->background slot) {:id :image})
        stack (->stack ctx [(draw-rect-actor)
                            image-widget])]
    (set-name! stack "inventory-cell")
    (set-id! stack cell)
    (add-listener! stack (proxy [ClickListener] []
                           (clicked [event x y]
                             (swap! state #(ctx/do! % (ctx/player-clicked-inventory % cell))))))
    stack))

(defn- slot->background [ctx]
  (let [sheet (spritesheet ctx "images/items.png" 48 48)]
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
                (let [drawable (->texture-region-drawable ctx
                                                          (:texture-region (get-sprite ctx sheet [21 (+ y 2)])))]
                  (.setMinSize drawable (float cell-size) (float cell-size))
                  [slot
                   (.tint ^TextureRegionDrawable drawable (graphics/->color 1 1 1 0.4))])))
         (into {}))))

; TODO move together with empty-inventory definition ?
(defn- redo-table! [ctx ^Table table slot->background]
  ; cannot do add-rows, need bag :position idx
  (let [cell (fn [& args] (apply ->cell ctx slot->background args))] ; TODO cell just return type hint ^Actor
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
    (doseq [y (range (grid/height (:inventory.slot/bag inventory/empty-inventory)))]
      (doseq [x (range (grid/width (:inventory.slot/bag inventory/empty-inventory)))]
        (.add table ^Actor (cell :inventory.slot/bag :position [x y])))
      (.row table))))

(defn ->build [ctx {:keys [slot->background]}]
  (let [table (->table ctx {:id ::table})]
    (redo-table! ctx table slot->background)
    (->window ctx {:title "Inventory"
                   :id :inventory-window
                   :visible? false
                   :pack? true
                   :position [(ctx/gui-viewport-width ctx)
                              (ctx/gui-viewport-height ctx)]
                   :rows [[{:actor table :pad 4}]]})))

(defn ->data [ctx]
  (slot->background ctx))

(defn- get-inventory [ctx]
  {:table (::table (get (:windows (ctx/get-stage ctx)) :inventory-window))
   :slot->background (:slot->background (:world/widgets ctx))})

(defcomponent :tx/set-item-image-in-widget
  (component/do! [[_ cell item] ctx]
    (let [{:keys [table]} (get-inventory ctx)
          cell-widget (get table cell)
          ^Image image-widget (get cell-widget :image)
          drawable (->texture-region-drawable ctx (:texture-region (:entity/image item)))]
      (.setMinSize drawable (float cell-size) (float cell-size))
      (.setDrawable image-widget drawable)
      (add-tooltip! cell-widget #(components/info-text item %))
      ctx)))

(defcomponent :tx/remove-item-from-widget
  (component/do! [[_ cell] ctx]
    (let [{:keys [table slot->background]} (get-inventory ctx)
          cell-widget (get table cell)
          ^Image image-widget (get cell-widget :image)]
      (.setDrawable image-widget (slot->background (cell 0)))
      (remove-tooltip! cell-widget)
      ctx)))
