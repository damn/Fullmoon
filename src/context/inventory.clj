(ns context.inventory
  (:require [core.component :refer [defcomponent] :as component]
            [data.grid2d :as grid]
            [app.state :refer [current-context]]
            [api.context :as ctx :refer [spritesheet get-sprite get-stage ->table ->window ->texture-region-drawable ->color ->stack ->image-widget
                                         player-tooltip-text transact-all!]]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.scene2d.actor :as actor :refer [set-id! add-listener! set-name! add-tooltip! remove-tooltip!]]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.tx :refer [transact!]]
            [entity.inventory :as inventory])
  (:import com.badlogic.gdx.scenes.scene2d.Actor
           (com.badlogic.gdx.scenes.scene2d.ui Widget Image Window Table)
           com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
           com.badlogic.gdx.scenes.scene2d.utils.ClickListener
           com.badlogic.gdx.math.Vector2))

(def ^:private cell-size 48)
(def ^:private droppable-color    [0   0.6 0 0.8])
(def ^:private not-allowed-color  [0.6 0   0 0.8])

(defn- draw-cell-rect [g player-entity x y mouseover? cell]
  (g/draw-rectangle g x y cell-size cell-size color/gray)
  (when (and mouseover?
             (= :item-on-cursor (entity/state @player-entity)))
    (let [item (:entity/item-on-cursor @player-entity)
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
      (let [{:keys [context/player-entity] g :context/graphics :as ctx} @current-context
            ^Widget this this]
        (draw-cell-rect g
                        player-entity
                        (.getX this)
                        (.getY this)
                        (mouseover? this (ctx/gui-mouse-position ctx))
                        (actor/id (actor/parent this)))))))

(defn- clicked-cell [{:keys [context/player-entity] :as ctx} cell]
  (let [entity* @player-entity]
    (state/clicked-inventory-cell (entity/state-obj entity*) entity* cell)))

(defn- ->cell [ctx slot->background slot & {:keys [position]}]
  (let [cell [slot (or position [0 0])]
        image-widget (->image-widget ctx (slot->background slot) {:id :image})
        stack (->stack ctx [(draw-rect-actor)
                            image-widget])]
    (set-name! stack "inventory-cell")
    (set-id! stack cell)
    (add-listener! stack (proxy [ClickListener] []
                           (clicked [event x y]
                             (let [ctx @current-context]
                               (transact-all! ctx (clicked-cell ctx cell))))))
    stack))

(defn- slot->background [ctx]
  (let [sheet (spritesheet ctx "items/images.png" 48 48)]
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
                   (.tint ^TextureRegionDrawable drawable (->color ctx 1 1 1 0.4))])))
         (into {}))))

; TODO move together with empty-inventory definition ?
(defn- redo-table! [ctx ^Table table slot->background]
  ; cannot do add-rows, need bag :position idx
  (let [cell (fn [& args] (apply ->cell ctx slot->background args))]
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
    (doseq [y (range (grid/height (:inventory.slot/bag inventory/empty-inventory)))]
      (doseq [x (range (grid/width (:inventory.slot/bag inventory/empty-inventory)))]
        (.add table ^Actor (cell :inventory.slot/bag :position [x y])))
      (.row table))))

(defcomponent :context/inventory {}
  (component/create [_ ctx]
    (let [table (->table ctx {})
          slot->background (slot->background ctx)]
      (redo-table! ctx table slot->background)
      {:window (->window ctx {:title "Inventory"
                              :id :inventory-window
                              :visible? false
                              :pack? true
                              :position [(ctx/gui-viewport-width ctx)
                                         (ctx/gui-viewport-height ctx)]
                              :rows [[{:actor table :pad 2}]]})
       :slot->background slot->background
       :table table})))

(extend-type api.context.Context
  api.context/InventoryWindow
  (inventory-window [{{:keys [window]} :context/inventory}]
    window))

(defmethod transact! :tx/set-item-image-in-widget [[_ cell item]
                                                   {{:keys [table]} :context/inventory :as ctx}]
  (let [^Actor cell-widget (get table cell)
        ^Image image-widget (get cell-widget :image)
        drawable (->texture-region-drawable ctx (:texture-region (:property/image item)))]
    (.setMinSize drawable (float cell-size) (float cell-size))
    (.setDrawable image-widget drawable)
    (add-tooltip! cell-widget #(player-tooltip-text % item))
    nil))

(defmethod transact! :tx/remove-item-from-widget [[_ cell]
                                                  {{:keys [table slot->background]} :context/inventory :as ctx}]
  (let [^Actor cell-widget (get table cell)
        ^Image image-widget (get cell-widget :image)]
    (.setDrawable image-widget (slot->background (cell 0)))
    (remove-tooltip! cell-widget)
    nil))
