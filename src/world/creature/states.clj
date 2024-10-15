(ns world.creature.states
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.input :refer [button-just-pressed? key-pressed?]]
            [clojure.gdx.math.vector :as v]
            [clojure.gdx.screen :as screen]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [mouse-on-actor?]]
            [core.component :refer [defc]]
            [core.effect :as effect]
            [core.tx :as tx]
            [world.core :as world]
            [world.entity :as entity]
            [world.entity.faction :as faction]
            [world.entity.inventory :refer [valid-slot? stackable? clicked-inventory-cell can-pickup-item? inventory-window]]
            [world.entity.skills :refer [has-skill? clicked-skillmenu-skill selected-skill]]
            [world.entity.state :as state]
            [world.entity.stats :refer [entity-stat]]
            [world.grid :as grid :refer [world-grid]]
            [world.mouseover-entity :refer [mouseover-entity]]
            [world.potential-fields :as potential-fields]
            [world.time :refer [->counter stopped? finished-ratio]]))

(defc :npc-dead
  {:let {:keys [eid]}}
  (entity/->v [[_ eid]]
    {:eid eid})

  (state/enter [_]
    [[:e/destroy eid]]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfindinusable skills every frame
; also prevents fast twitching around changing directions every frame
(defc :npc-moving
  {:let {:keys [eid movement-vector counter]}}
  (entity/->v [[_ eid movement-vector]]
    {:eid eid
     :movement-vector movement-vector
     :counter (->counter (* (entity-stat @eid :stats/reaction-time) 0.016))})

  (state/enter [_]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (or (entity-stat @eid :stats/movement-speed) 0)}]])

  (state/exit [_]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid]
    (when (stopped? counter)
      [[:tx/event eid :timer-finished]])))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities world-grid)
       (filter #(= (:entity/faction @%) faction))))

(defc :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (entity/tick [_ eid]
    (when (stopped? counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defc :tx/shout
  (tx/do! [[_ position faction delay-seconds]]
    [[:e/create
      position
      entity/effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter delay-seconds)
        :faction faction}}]]))

(defc :npc-sleeping
  {:let {:keys [eid]}}
  (entity/->v [[_ eid]]
    {:eid eid})

  (state/exit [_]
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (entity/tick [_ eid]
    (let [entity @eid
          cell (world-grid (entity/tile entity))]
      (when-let [distance (grid/nearest-entity-distance @cell (faction/enemy entity))]
        (when (<= distance (entity-stat entity :stats/aggro-range))
          [[:tx/event eid :alert]]))))

  (entity/render-above [_ entity]
    (let [[x y] (:position entity)]
      (g/draw-text {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity))
                    :up? true}))))

(defc :player-dead
  (state/player-enter [_]
    [[:tx/cursor :cursors/black-x]])

  (state/pause-game? [_]
    true)

  (state/enter [_]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(screen/change! :screens/main-menu)}]]))

(defn- clicked-cell [{:keys [entity/id] :as entity} cell]
  (let [inventory (:entity/inventory entity)
        item-in-cell (get-in inventory cell)
        item-on-cursor (:entity/item-on-cursor entity)]
    (cond
     ; PUT ITEM IN EMPTY CELL
     (and (not item-in-cell)
          (valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/set-item id cell item-on-cursor]
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; STACK ITEMS
     (and item-in-cell
          (stackable? item-in-cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item id cell item-on-cursor]
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; SWAP ITEMS
     (and item-in-cell
          (valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/remove-item id cell]
      [:tx/set-item id cell item-on-cursor]
      ; need to dissoc and drop otherwise state enter does not trigger picking it up again
      ; TODO? coud handle pickup-item from item-on-cursor state also
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]
      [:tx/event id :pickup-item item-in-cell]])))

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
    (clicked-cell @eid cell))

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

(defn- add-vs [vs]
  (v/normalise (reduce v/add [0 0] vs)))

(defn- WASD-movement-vector []
  (let [r (when (key-pressed? :d) [1  0])
        l (when (key-pressed? :a) [-1 0])
        u (when (key-pressed? :w) [0  1])
        d (when (key-pressed? :s) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v/length v))
          v)))))

(defc :player-moving
  {:let {:keys [eid movement-vector]}}
  (entity/->v [[_ eid movement-vector]]
    {:eid eid
     :movement-vector movement-vector})

  (state/player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (state/pause-game? [_]
    false)

  (state/enter [_]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity-stat @eid :stats/movement-speed)}]])

  (state/exit [_]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity-stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))

(defc :stunned
  {:let {:keys [eid counter]}}
  (entity/->v [[_ eid duration]]
    {:eid eid
     :counter (->counter duration)})

  (state/player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (state/pause-game? [_]
    false)

  (entity/tick [_ eid]
    (when (stopped? counter)
      [[:tx/event eid :effect-wears-off]]))

  (entity/render-below [_ entity]
    (g/draw-circle (:position entity) 0.5 [1 1 1 0.6])))

(defn- draw-skill-icon [icon entity [x y] action-counter-ratio]
  (let [[width height] (:world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ (float width) 2)
        y (+ (float y) (float (:half-height entity)) (float 0.15))
        center [x (+ y radius)]]
    (g/draw-filled-circle center radius [1 1 1 0.125])
    (g/draw-sector center radius
                   90 ; start-angle
                   (* (float action-counter-ratio) 360) ; degree
                   [1 1 1 0.5])
    (g/draw-image icon [(- (float x) radius) y])))

(defn- apply-action-speed-modifier [entity skill action-time]
  (/ action-time
     (or (entity-stat entity (:skill/action-time-modifier-key skill))
         1)))

(defc :active-skill
  {:let {:keys [eid skill effect-ctx counter]}}
  (entity/->v [[_ eid [skill effect-ctx]]]
    {:eid eid
     :skill skill
     :effect-ctx effect-ctx
     :counter (->> skill
                   :skill/action-time
                   (apply-action-speed-modifier @eid skill)
                   (->counter))})

  (state/player-enter [_]
    [[:tx/cursor :cursors/sandclock]])

  (state/pause-game? [_]
    false)

  (state/enter [_]
    [[:tx/sound (:skill/start-action-sound skill)]
     (when (:skill/cooldown skill)
       [:e/assoc-in eid [:entity/skills (:property/id skill) :skill/cooling-down?] (->counter (:skill/cooldown skill))])
     (when-not (zero? (:skill/cost skill))
       [:tx.entity.stats/pay-mana-cost eid (:skill/cost skill)])])

  (entity/tick [_ eid]
    (cond
     (effect/with-ctx (effect/check-update-ctx effect-ctx)
       (not (effect/effect-applicable? (:skill/effects skill))))
     [[:tx/event eid :action-done]
      ; TODO some sound ?
      ]

     (stopped? counter)
     [[:tx/event eid :action-done]
      [:tx/effect effect-ctx (:skill/effects skill)]]))

  (entity/render-info [_ entity]
    (let [{:keys [entity/image skill/effects]} skill]
      (draw-skill-icon image entity (:position entity) (finished-ratio counter))
      (effect/with-ctx (effect/check-update-ctx effect-ctx)
        (run! effect/render! effects)))))

(defn- mana-value [entity]
  (if-let [mana (entity-stat entity :stats/mana)]
    (mana 0)
    0))

(defn- not-enough-mana? [entity {:keys [skill/cost]}]
  (> cost (mana-value entity)))

(defn- skill-usable-state
  [entity {:keys [skill/cooling-down? skill/effects] :as skill}]
  (cond
   cooling-down?
   :cooldown

   (not-enough-mana? entity skill)
   :not-enough-mana

   (not (effect/effect-applicable? effects))
   :invalid-params

   :else
   :usable))

(defn- npc-choose-skill [entity]
  (->> entity
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill-usable-state entity %))
                     (effect/effect-useful? (:skill/effects %))))
       first))

(comment
 (let [entity @(entity/get-entity 76)
       effect-ctx (effect/npc-ctx entity)]
   (npc-choose-skill effect-ctx entity))
 )

(defc :npc-idle
  {:let {:keys [eid]}}
  (entity/->v [[_ eid]]
    {:eid eid})

  (entity/tick [_ eid]
    (let [entity @eid
          effect-ctx (effect/npc-ctx entity)]
      (if-let [skill (effect/with-ctx effect-ctx
                       (npc-choose-skill entity))]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (potential-fields/follow-to-enemy eid)]]))))

(defn- denied [text]
  [[:tx/sound "sounds/bfxr_denied.wav"]
   [:tx/msg-to-player text]])

(defmulti ^:private on-clicked
  (fn [entity]
    (:type (:entity/clickable entity))))

(defmethod on-clicked :clickable/item [clicked-entity]
  (let [player-entity @world/player
        item (:entity/item clicked-entity)
        clicked-entity (:entity/id clicked-entity)]
    (cond
     (a/visible? (inventory-window))
     [[:tx/sound "sounds/bfxr_takeit.wav"]
      [:e/destroy clicked-entity]
      [:tx/event (:entity/id player-entity) :pickup-item item]]

     (can-pickup-item? player-entity item)
     [[:tx/sound "sounds/bfxr_pickup.wav"]
      [:e/destroy clicked-entity]
      [:tx/pickup-item (:entity/id player-entity) item]]

     :else
     [[:tx/sound "sounds/bfxr_denied.wav"]
      [:tx/msg-to-player "Your Inventory is full"]])))

(defmethod on-clicked :clickable/player
  [_clicked-entity]
  (a/toggle-visible! (inventory-window))) ; TODO no tx

(defn- clickable->cursor [mouseover-e* too-far-away?]
  (case (:type (:entity/clickable mouseover-e*))
    :clickable/item (if too-far-away?
                      :cursors/hand-before-grab-gray
                      :cursors/hand-before-grab)
    :clickable/player :cursors/bag))

(defn- ->clickable-mouseover-entity-interaction [player-e* mouseover-e*]
  (if (< (v/distance (:position player-e*) (:position mouseover-e*))
         (:entity/click-distance-tiles player-e*))
    [(clickable->cursor mouseover-e* false) (fn [] (on-clicked mouseover-e*))]
    [(clickable->cursor mouseover-e* true)  (fn [] (denied "Too far away"))]))

(defn- inventory-cell-with-item? [actor]
  (and (a/parent actor)
       (= "inventory-cell" (a/name (a/parent actor)))
       (get-in (:entity/inventory @world/player)
               (a/id (a/parent actor)))))

(defn- mouseover-actor->cursor []
  (let [actor (mouse-on-actor?)]
    (cond
     (inventory-cell-with-item? actor) :cursors/hand-before-grab
     (ui/window-title-bar? actor) :cursors/move-window
     (ui/button? actor) :cursors/over-button
     :else :cursors/default)))

(defn- ->interaction-state [entity]
  (let [mouseover-e* (mouseover-entity)]
    (cond
     (mouse-on-actor?)
     [(mouseover-actor->cursor) (fn [] nil)] ; handled by actors themself, they check player state

     (and mouseover-e* (:entity/clickable mouseover-e*))
     (->clickable-mouseover-entity-interaction entity mouseover-e*)

     :else
     (if-let [skill-id (selected-skill)]
       (let [skill (skill-id (:entity/skills entity))
             effect-ctx (effect/player-ctx entity)
             state (effect/with-ctx effect-ctx
                     (skill-usable-state entity skill))]
         (if (= state :usable)
           (do
            ; TODO cursor AS OF SKILL effect (SWORD !) / show already what the effect would do ? e.g. if it would kill highlight
            ; different color ?
            ; => e.g. meditation no TARGET .. etc.
            [:cursors/use-skill
             (fn []
               [[:tx/event (:entity/id entity) :start-action [skill effect-ctx]]])])
           (do
            ; TODO cursor as of usable state
            ; cooldown -> sanduhr kleine
            ; not-enough-mana x mit kreis?
            ; invalid-params -> depends on params ...
            [:cursors/skill-not-usable
             (fn []
               (denied (case state
                         :cooldown "Skill is still on cooldown"
                         :not-enough-mana "Not enough mana"
                         :invalid-params "Cannot use this here")))])))
       [:cursors/no-skill-selected
        (fn [] (denied "No selected skill"))]))))

(defc :player-idle
  {:let {:keys [eid]}}
  (entity/->v [[_ eid]]
    {:eid eid})

  (state/pause-game? [_]
    true)

  (state/manual-tick [_]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/event eid :movement-input movement-vector]]
      (let [[cursor on-click] (->interaction-state @eid)]
        (cons [:tx/cursor cursor]
              (when (button-just-pressed? :left)
                (on-click))))))

  (clicked-inventory-cell [_ cell]
    ; TODO no else case
    (when-let [item (get-in (:entity/inventory @eid) cell)]
      [[:tx/sound "sounds/bfxr_takeit.wav"]
       [:tx/event eid :pickup-item item]
       [:tx/remove-item eid cell]]))

  (clicked-skillmenu-skill [_ skill]
    (let [free-skill-points (:entity/free-skill-points @eid)]
      ; TODO no else case, no visible free-skill-points
      (when (and (pos? free-skill-points)
                 (not (has-skill? @eid skill)))
        [[:e/assoc eid :entity/free-skill-points (dec free-skill-points)]
         [:tx/add-skill eid skill]]))))
