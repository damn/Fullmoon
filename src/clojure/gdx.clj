(ns clojure.gdx
  (:require [clojure.gdx.audio :refer [play-sound!]]
            [clojure.gdx.graphics :as g]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [stage-get stage-add!]]
            [clojure.string :as str]
            [core.component :refer [defsystem defc] :as component]
            [core.data :as data]
            [core.effect :refer [do! effect!]]
            [core.operation :as op]
            [core.db :as db]
            [core.property :as property]
            [data.grid2d :as g2d]
            [malli.core :as m]
            [utils.core :refer [find-first readable-number]]
            [world.creature.faction :as faction]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [world.grid :as grid :refer [world-grid]]
            [world.player :refer [world-player]]
            [world.time :refer [->counter stopped? world-delta finished-ratio]]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (entity-stat [_ stat] "Calculating value of the stat w. modifiers"))

(defprotocol Modifiers
  (->modified-value [_ modifier-k base-value]))

(defc :entity/image
  {:data :image
   :let image}
  (entity/render [_ entity*]
    (g/draw-rotated-centered-image image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))

(defprotocol Animation
  (^:private anim-tick [_ delta])
  (^:private restart [_])
  (^:private anim-stopped? [_])
  (^:private current-frame [_]))

(defrecord ImmutableAnimation [frames frame-duration looping? cnt maxcnt]
  Animation
  (anim-tick [this delta]
    (let [maxcnt (float maxcnt)
          newcnt (+ (float cnt) (float delta))]
      (assoc this :cnt (cond (< newcnt maxcnt) newcnt
                             looping? (min maxcnt (- newcnt maxcnt))
                             :else maxcnt))))

  (restart [this]
    (assoc this :cnt 0))

  (anim-stopped? [_]
    (and (not looping?) (>= cnt maxcnt)))

  (current-frame [this]
    (frames (min (int (/ (float cnt) (float frame-duration)))
                 (dec (count frames))))))

(defn- ->animation [frames & {:keys [frame-duration looping?]}]
  (map->ImmutableAnimation
    {:frames (vec frames)
     :frame-duration frame-duration
     :looping? looping?
     :cnt 0
     :maxcnt (* (count frames) (float frame-duration))}))

(defn- edn->animation [{:keys [frames frame-duration looping?]}]
  (->animation (map g/edn->image frames)
               :frame-duration frame-duration
               :looping? looping?))


(defmethod db/edn->value :data/animation [_ animation]
  (edn->animation animation))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defc :entity/animation
  {:data :data/animation
   :let animation}
  (entity/create [_ eid]
    [(tx-assoc-image-current-frame eid animation)])

  (entity/tick [[k _] eid]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (anim-tick animation world-delta)]]))

(defc :entity/delete-after-animation-stopped?
  (entity/create [_ entity]
    (-> @entity :entity/animation :looping? not assert))

  (entity/tick [_ entity]
    (when (anim-stopped? (:entity/animation @entity))
      [[:e/destroy entity]])))

(property/def :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defc :tx/audiovisual
  (do! [[_ position id]]
    (let [{:keys [tx/sound
                  entity/animation]} (db/get id)]
      [[:tx/sound sound]
       [:e/create
        position
        entity/effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))

(defc :entity/delete-after-duration
  {:let counter}
  (component/create [[_ duration]]
    (->counter duration))

  (component/info [_]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (finished-ratio counter)) "/1[]"))

  (entity/tick [_ eid]
    (when (stopped? counter)
      [[:e/destroy eid]])))

(defc :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (entity/destroy [_ entity]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))

(defc :entity/line-render
  {:let {:keys [thick? end color]}}
  (entity/render [_ entity*]
    (let [position (:position entity*)]
      (if thick?
        (g/with-shape-line-width 4 #(g/draw-line position end color))
        (g/draw-line position end color)))))

(defc :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}]]
    [[:e/create
      start
      entity/effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))

(property/def :properties/skills
  {:schema [:entity/image
            :property/pretty-name
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/start-action-sound
            :skill/effects
            [:skill/cooldown {:optional true}]
            [:skill/cost {:optional true}]]
   :overview {:title "Skills"
              :columns 16
              :image/scale 2}})

(defsystem clicked-skillmenu-skill [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])

(defn- player-clicked-skillmenu [skill]
  (clicked-skillmenu-skill (entity-state/state-obj @world-player) skill))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @world-player))
#_(defn ->skill-window []
    (ui/window {:title "Skills"
                :id :skill-window
                :visible? false
                :cell-defaults {:pad 10}
                :rows [(for [id [:skills/projectile
                                 :skills/meditation
                                 :skills/spawn
                                 :skills/melee-attack]
                             :let [; get-property in callbacks if they get changed, this is part of context permanently
                                   button (ui/image-button ; TODO reuse actionbar button scale?
                                                           (:entity/image (db/get id)) ; TODO here anyway taken
                                                           ; => should probably build this window @ game start
                                                           (fn []
                                                             (effect! (player-clicked-skillmenu (db/get id)))))]]
                         (do
                          (ui/add-tooltip! button #(component/info-text (db/get id))) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))

(defc :skill/action-time {:data :pos}
  (component/info [[_ v]]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defc :skill/cooldown {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defc :skill/cost {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defc :skill/effects
  {:data [:components-ns :effect]})

(defc :skill/start-action-sound {:data :sound})

(defc :skill/action-time-modifier-key
  {:data [:enum :stats/cast-speed :stats/attack-speed]}
  (component/info [[_ v]]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defc :entity/skills
  {:data [:one-to-many :properties/skills]}
  (entity/create [[k skills] eid]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (component/info [[_ skills]]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (entity/tick [[k skills] eid]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defc :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defc :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))

(defc :tx.entity.stats/pay-mana-cost
  (do! [[_ entity cost]]
    (let [mana-val ((entity-stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )

(defc :entity/clickable
  (entity/render [[_ {:keys [text]}]
           {:keys [entity/mouseover?] :as entity*}]
    (when (and mouseover? text)
      (let [[x y] (:position entity*)]
        (g/draw-text {:text text
                      :x x
                      :y (+ y (:half-height entity*))
                      :up? true})))))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defc :entity/mouseover?
  (entity/render-below [_ {:keys [entity/faction] :as entity*}]
    (let [player-entity* @world-player]
      (g/with-shape-line-width 3
        #(g/draw-ellipse (:position entity*)
                         (:half-width entity*)
                         (:half-height entity*)
                         (cond (= faction (faction/enemy player-entity*))
                               enemy-color
                               (= faction (faction/friend player-entity*))
                               friendly-color
                               :else
                               neutral-color))))))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities world-grid)
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defc :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (entity/tick [_ eid]
    (when (stopped? counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defc :tx/shout
  (do! [[_ position faction delay-seconds]]
    [[:e/create
      position
      entity/effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter delay-seconds)
        :faction faction}}]]))

(def hpbar-height-px 5)

(defc :entity/string-effect
  (entity/tick [[k {:keys [counter]}] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity*]
    (let [[x y] (:position entity*)]
      (g/draw-text {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defc :tx/add-text-effect
  (do! [[_ entity text]]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter world.time/reset))
        {:text text
         :counter (->counter 0.4)})]]))

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:e/update-in entity [:entity/modifiers modifier-k operation-k] (f value)]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-one [coll item]
  (let [[n m] (split-with (partial not= item) coll)]
    (concat n (rest m))))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (remove-one values value)))

(defc :tx/apply-modifiers
  (do! [[_ entity modifiers]]
    (txs-update-modifiers entity modifiers conj-value)))

(defc :tx/reverse-modifiers
  (do! [[_ entity modifiers]]
    (txs-update-modifiers entity modifiers remove-value)))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [_value] :fn))
    [[:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-inc] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-mult] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

; DRY ->effective-value (summing)
; also: sort-by op/order @ modifier/info-text itself (so player will see applied order)
(defn- sum-operation-values [modifiers]
  (for [[modifier-k operations] modifiers
        :let [operations (for [[operation-k values] operations
                               :let [value (apply + values)]
                               :when (not (zero? value))]
                           [operation-k value])]
        :when (seq operations)]
    [modifier-k operations]))

(g/def-markup-color "MODIFIER_BLUE" :cyan)

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
; -> could give damage reduce 10% like in diablo 2
; and then make it negative .... @ applicator
(def ^:private positive-modifier-color "[MODIFIER_BLUE]" #_"[LIME]")
(def ^:private negative-modifier-color "[MODIFIER_BLUE]" #_"[SCARLET]")

(defn k->pretty-name [k]
  (str/capitalize (name k)))

(defn mod-info-text [modifiers]
  (str "[MODIFIER_BLUE]"
       (str/join "\n"
                 (for [[modifier-k operations] modifiers
                       operation operations]
                   (str (op/info-text operation) " " (k->pretty-name modifier-k))))
       "[]"))

(defc :entity/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/create [_]
    (into {} (for [[modifier-k operations] modifiers]
               [modifier-k (into {} (for [[operation-k value] operations]
                                      [operation-k [value]]))])))

  (component/info [_]
    (let [modifiers (sum-operation-values modifiers)]
      (when (seq modifiers)
        (mod-info-text modifiers)))))

(extend-type world.entity.Entity
  Modifiers
  (->modified-value [{:keys [entity/modifiers]} modifier-k base-value]
    {:pre [(= "modifier" (namespace modifier-k))]}
    (->> modifiers
         modifier-k
         (sort-by op/order)
         (reduce (fn [base-value [operation-k values]]
                   (op/apply [operation-k (apply + values)] base-value))
                 base-value))))

(comment

 (let [->entity (fn [modifiers]
                  (entity/map->Entity {:entity/modifiers modifiers}))]
   (and
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]
                                                           :op/val-mult [0.5]}})
                         :modifier/damage-deal
                         [5 10])
       [52 52])
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]}
                                    :stats/fooz-barz {:op/babu [1 2 3]}})
                         :modifier/damage-deal
                         [5 10])
       [35 35])
    (= (->modified-value (entity/map->Entity {})
                         :modifier/damage-deal
                         [5 10])
       [5 10])
    (= (->modified-value (->entity {:modifier/hp {:op/max-inc [10 1]
                                                  :op/max-mult [0.5]}})
                         :modifier/hp
                         [100 100])
       [100 166])
    (= (->modified-value (->entity {:modifier/movement-speed {:op/inc [2]
                                                              :op/mult [0.1 0.2]}})
                         :modifier/movement-speed
                         3)
       6.5)))
 )

(g/def-markup-color "ITEM_GOLD" [0.84 0.8 0.52])

(defc :property/pretty-name
  {:data :string
   :let value}
  (component/info [_]
    (str "[ITEM_GOLD]"value"[]")))

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
              [slot (g2d/create-grid width height (constantly nil))]))
       (into {})))

(defc :item/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/info [_]
    (when (seq modifiers)
      (mod-info-text modifiers))))

(defc :item/slot
  {:data (apply vector :enum (keys empty-inventory))})

(def ^:private body-props
  {:width 0.75
   :height 0.75
   :z-order :z-order/on-ground})

(defc :tx/item
  (do! [[_ position item]]
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

(defc :tx/set-item
  (do! [[_ entity cell item]]
    (set-item @entity cell item)))

(defc :tx/remove-item
  (do! [[_ entity cell]]
    (remove-item @entity cell)))

; TODO doesnt exist, stackable, usable items with action/skillbar thingy
#_(defn remove-one-item [entity cell]
  (let [item (get-in (:entity/inventory @entity) cell)]
    (if (and (:count item)
             (> (:count item) 1))
      (do
       ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
       ; first remove and then place, just update directly  item ...
       (remove-item! entity cell)
       (set-item! entity cell (update item :count dec)))
      (remove-item! entity cell))))

; TODO no items which stack are available
(defn- stack-item [entity* cell item]
  (let [cell-item (get-in (:entity/inventory entity*) cell)]
    (assert (stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item entity* cell)
            (set-item entity* cell (update cell-item :count + (:count item))))))

(defc :tx/stack-item
  (do! [[_ entity cell item]]
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

(defc :tx/pickup-item
  (do! [[_ entity item]]
    (pickup-item @entity item)))

(extend-type world.entity.Entity
  Inventory
  (can-pickup-item? [entity* item]
    (boolean (pickup-item entity* item))))

(defc :entity/inventory
  {:data [:one-to-many :properties/items]}
  (entity/create [[_ items] eid]
    (cons [:e/assoc eid :entity/inventory empty-inventory]
          (for [item items]
            [:tx/pickup-item eid item]))))


; Items are also smaller than 48x48 all of them
; so wasting space ...
; can maybe make a smaller textureatlas or something...

(def ^:private cell-size 48)
(def ^:private droppable-color    [0   0.6 0 0.8])
(def ^:private not-allowed-color  [0.6 0   0 0.8])

(defn- draw-cell-rect [player-entity* x y mouseover? cell]
  (g/draw-rectangle x y cell-size cell-size :gray)
  (when (and mouseover?
             (= :player-item-on-cursor (entity-state/state-k player-entity*)))
    (let [item (:entity/item-on-cursor player-entity*)
          color (if (valid-slot? cell item)
                  droppable-color
                  not-allowed-color)]
      (g/draw-filled-rectangle (inc x) (inc y) (- cell-size 2) (- cell-size 2) color))))

; TODO why do I need to call getX ?
; is not layouted automatically to cell , use 0/0 ??
; (maybe (.setTransform stack true) ? , but docs say it should work anyway
(defn- draw-rect-actor []
  (ui/widget
   (fn [this]
     (draw-cell-rect @world-player
                     (a/x this)
                     (a/y this)
                     (a/mouseover? this (g/gui-mouse-position))
                     (a/id (a/parent this))))))

(defsystem clicked-inventory-cell [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defn- player-clicked-inventory [cell]
  (clicked-inventory-cell (entity-state/state-obj @world-player) cell))

(defn- ->cell [slot->background slot & {:keys [position]}]
  (let [cell [slot (or position [0 0])]
        image-widget (ui/image-widget (slot->background slot) {:id :image})
        stack (ui/stack [(draw-rect-actor) image-widget])]
    (a/set-name! stack "inventory-cell")
    (a/set-id! stack cell)
    (a/add-listener! stack (proxy [com.badlogic.gdx.scenes.scene2d.utils.ClickListener] []
                             (clicked [event x y]
                               (effect! (player-clicked-inventory cell)))))
    stack))

(defn- slot->background []
  (let [sheet (g/sprite-sheet "images/items.png" 48 48)]
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
                (let [drawable (ui/texture-region-drawable (:texture-region (g/sprite sheet [21 (+ y 2)])))]
                  (ui/set-min-size! drawable cell-size)
                  [slot
                   (ui/tinted-drawable drawable (g/->color 1 1 1 0.4))])))
         (into {}))))

(import 'com.badlogic.gdx.scenes.scene2d.ui.Table)

; TODO move together with empty-inventory definition ?
(defn- redo-table! [^Table table slot->background]
  ; cannot do add-rows, need bag :position idx
  (let [cell (fn [& args] (apply ->cell slot->background args))]
    (.clear table) ; no need as we create new table ... TODO
    (doto table .add .add
      (.add (cell :inventory.slot/helm))
      (.add (cell :inventory.slot/necklace)) .row)
    (doto table .add
      (.add (cell :inventory.slot/weapon))
      (.add (cell :inventory.slot/chest))
      (.add (cell :inventory.slot/cloak))
      (.add (cell :inventory.slot/shield)) .row)
    (doto table .add .add
      (.add (cell :inventory.slot/leg)) .row)
    (doto table .add
      (.add (cell :inventory.slot/glove))
      (.add (cell :inventory.slot/rings :position [0 0]))
      (.add (cell :inventory.slot/rings :position [1 0]))
      (.add (cell :inventory.slot/boot)) .row)
    ; TODO add separator
    (doseq [y (range (g2d/height (:inventory.slot/bag empty-inventory)))]
      (doseq [x (range (g2d/width (:inventory.slot/bag empty-inventory)))]
        (.add table (cell :inventory.slot/bag :position [x y])))
      (.row table))))

(defn ->inventory-window [{:keys [slot->background]}]
  (let [table (ui/table {:id ::table})]
    (redo-table! table slot->background)
    (ui/window {:title "Inventory"
                :id :inventory-window
                :visible? false
                :pack? true
                :position [(g/gui-viewport-width)
                           (g/gui-viewport-height)]
                :rows [[{:actor table :pad 4}]]})))

(defn ->inventory-window-data [] (slot->background))

(declare world-widgets)

(defn- get-inventory []
  {:table (::table (get (:windows (stage-get)) :inventory-window))
   :slot->background (:slot->background world-widgets)})

(defc :tx/set-item-image-in-widget
  (do! [[_ cell item]]
    (let [{:keys [table]} (get-inventory)
          cell-widget (get table cell)
          image-widget (get cell-widget :image)
          drawable (ui/texture-region-drawable (:texture-region (:entity/image item)))]
      (ui/set-min-size! drawable cell-size)
      (ui/set-drawable! image-widget drawable)
      (ui/add-tooltip! cell-widget #(component/info-text item))
      nil)))

(defc :tx/remove-item-from-widget
  (do! [[_ cell]]
    (let [{:keys [table slot->background]} (get-inventory)
          cell-widget (get table cell)
          image-widget (get cell-widget :image)]
      (ui/set-drawable! image-widget (slot->background (cell 0)))
      (ui/remove-tooltip! cell-widget)
      nil)))

(defsystem pause-game?)
(defmethod pause-game? :default [_])

(defsystem manual-tick)
(defmethod manual-tick :default [_])

(defc :tx/sound
  {:data :sound}
  (do! [[_ file]]
    (play-sound! file)
    nil))

(defc :tx/cursor
  (do! [[_ cursor-key]]
    (g/set-cursor! cursor-key)
    nil))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [{:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get))))
  (stage-add! (ui/window {:title title
                          :rows [[(ui/label text)]
                                 [(ui/text-button button-text
                                                  (fn []
                                                    (a/remove! (::modal (stage-get)))
                                                    (on-click)))]]
                          :id ::modal
                          :modal? true
                          :center-position [(/ (g/gui-viewport-width) 2)
                                            (* (g/gui-viewport-height) (/ 3 4))]
                          :pack? true})))

(defc :tx/player-modal
  (do! [[_ params]]
    (show-player-modal! params)
    nil))
