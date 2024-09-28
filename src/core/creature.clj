(ns core.creature
  (:require [clojure.ctx :refer :all]
            [clojure.gdx :refer :all]
            [clojure.string :as str]
            [reduce-fsm :as fsm]
            [core.item :as item]))

(def-type :properties/creatures
  {:schema [:entity/body
            :property/pretty-name
            :creature/species
            :creature/level
            :entity/animation
            :entity/stats
            :entity/skills
            [:entity/modifiers {:optional true}]
            [:entity/inventory {:optional true}]]
   :overview {:title "Creatures"
              :columns 15
              :image/scale 1.5
              :sort-by-fn #(vector (:creature/level %)
                                   (name (:creature/species %))
                                   (name (:property/id %)))
              :extra-info-text #(str (:creature/level %))}})

(def-attributes
  :body/width   :pos
  :body/height  :pos
  :body/flying? :boolean)

; player doesn;t need aggro-range/reaction-time
; stats armor-pierce wrong place
; assert min body size from core.entity

(defcomponent :entity/body
  {:data [:map [:body/width
                :body/height
                :body/flying?]]})

(defcomponent :creature/species
  {:data [:qualified-keyword {:namespace :species}]}
  (->mk [[_ species] _ctx]
    (str/capitalize (name species)))
  (info-text [[_ species] _ctx]
    (str "[LIGHT_GRAY]Creature - " species "[]")))

(defcomponent :creature/level
  {:data :pos-int}
  (info-text [[_ lvl] _ctx]
    (str "[GRAY]Level " lvl "[]")))

; # :z-order/flying has no effect for now
; * entities with :z-order/flying are not flying over water,etc. (movement/air)
; because using potential-field for z-order/ground
; -> would have to add one more potential-field for each faction for z-order/flying
; * they would also (maybe) need a separate occupied-cells if they don't collide with other
; * they could also go over ground units and not collide with them
; ( a test showed then flying OVER player entity )
; -> so no flying units for now
(defn- ->body [{:keys [body/width body/height #_body/flying?]}]
  {:width  width
   :height height
   :collides? true
   :z-order :z-order/ground #_(if flying? :z-order/flying :z-order/ground)})

(defcomponent :tx/creature
  {:let {:keys [position creature-id components]}}
  (do! [_ ctx]
    (let [props (build-property ctx creature-id)]
      [[:e/create
        position
        (->body (:entity/body props))
        (-> props
            (dissoc :entity/body)
            (assoc :entity/destroy-audiovisual :audiovisuals/creature-die)
            (safe-merge components))]])))

(comment
 ; graphviz required in path
 (fsm/show-fsm player-fsm)

 )

(fsm/defsm-inc ^:private player-fsm
  [[:player-idle
    :kill -> :player-dead
    :stun -> :stunned
    :start-action -> :active-skill
    :pickup-item -> :player-item-on-cursor
    :movement-input -> :player-moving]
   [:player-moving
    :kill -> :player-dead
    :stun -> :stunned
    :no-movement-input -> :player-idle]
   [:active-skill
    :kill -> :player-dead
    :stun -> :stunned
    :action-done -> :player-idle]
   [:stunned
    :kill -> :player-dead
    :effect-wears-off -> :player-idle]
   [:player-item-on-cursor
    :kill -> :player-dead
    :stun -> :stunned
    :drop-item -> :player-idle
    :dropped-item -> :player-idle]
   [:player-dead]])

(fsm/defsm-inc ^:private npc-fsm
  [[:npc-sleeping
    :kill -> :npc-dead
    :stun -> :stunned
    :alert -> :npc-idle]
   [:npc-idle
    :kill -> :npc-dead
    :stun -> :stunned
    :start-action -> :active-skill
    :movement-direction -> :npc-moving]
   [:npc-moving
    :kill -> :npc-dead
    :stun -> :stunned
    :timer-finished -> :npc-idle]
   [:active-skill
    :kill -> :npc-dead
    :stun -> :stunned
    :action-done -> :npc-idle]
   [:stunned
    :kill -> :npc-dead
    :effect-wears-off -> :npc-idle]
   [:npc-dead]])

; fsm throws when initial-state is not part of states, so no need to assert initial-state
; initial state is nil, so associng it. make bug report at reduce-fsm?
(defn- ->init-fsm [fsm initial-state]
  (assoc (fsm initial-state nil) :state initial-state))

(defcomponent :entity/state
  (->mk [[_ [player-or-npc initial-state]] _ctx]
    {:initial-state initial-state
     :fsm (case player-or-npc
            :state/player player-fsm
            :state/npc npc-fsm)})

  (create [[k {:keys [fsm initial-state]}] eid ctx]
    [[:e/assoc eid k (->init-fsm fsm initial-state)]
     [:e/assoc eid initial-state (->mk [initial-state eid] ctx)]])

  (info-text [[_ fsm] _ctx]
    (str "[YELLOW]State: " (name (:state fsm)) "[]")))

(extend-type clojure.ctx.Entity
  State
  (entity-state [entity*]
    (-> entity* :entity/state :state))

  (state-obj [entity*]
    (let [state-k (entity-state entity*)]
      [state-k (state-k entity*)])))

(defn- send-event! [ctx eid event params]
  (when-let [fsm (:entity/state @eid)]
    (let [old-state-k (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state-k (:state new-fsm)]
      (when-not (= old-state-k new-state-k)
        (let [old-state-obj (state-obj @eid)
              new-state-obj [new-state-k (->mk [new-state-k eid params] ctx)]]
          [#(exit old-state-obj %)
           #(enter new-state-obj %)
           (when (:entity/player? @eid)
             (fn [_ctx] (player-enter new-state-obj)))
           [:e/assoc eid :entity/state new-fsm]
           [:e/dissoc eid old-state-k]
           [:e/assoc eid new-state-k (new-state-obj 1)]])))))

(defcomponent :tx/event
  (do! [[_ eid event params] ctx]
    (send-event! ctx eid event params)))

(defcomponent :npc-dead
  {:let {:keys [eid]}}
  (->mk [[_ eid] _ctx]
    {:eid eid})

  (enter [_ _ctx]
    [[:e/destroy eid]]))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfindinusable skills every frame
; also prevents fast twitching around changing directions every frame
(defcomponent :npc-moving
  {:let {:keys [eid movement-vector counter]}}
  (->mk [[_ eid movement-vector] ctx]
    {:eid eid
     :movement-vector movement-vector
     :counter (->counter ctx (* (entity-stat @eid :stats/reaction-time) 0.016))})

  (enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (or (entity-stat @eid :stats/movement-speed) 0)}]])

  (exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :timer-finished]])))

(defcomponent :npc-sleeping
  {:let {:keys [eid]}}
  (->mk [[_ eid] _ctx]
    {:eid eid})

  (exit [_ ctx]
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (tick [_ eid ctx]
    (let [entity* @eid
          cell ((:context/grid ctx) (entity-tile entity*))]
      (when-let [distance (nearest-entity-distance @cell (enemy-faction entity*))]
        (when (<= distance (entity-stat entity* :stats/aggro-range))
          [[:tx/event eid :alert]]))))

  (render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (draw-text g
                 {:text "zzz"
                  :x x
                  :y (+ y (:half-height entity*))
                  :up? true}))))

(defcomponent :player-dead
  (player-enter [_]
    [[:tx/cursor :cursors/black-x]])

  (pause-game? [_]
    true)

  (enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(change-screen % :screens/main-menu)}]]))

(defn- clicked-cell [{:keys [entity/id] :as entity*} cell]
  (let [inventory (:entity/inventory entity*)
        item-in-cell (get-in inventory cell)
        item-on-cursor (:entity/item-on-cursor entity*)]
    (cond
     ; PUT ITEM IN EMPTY CELL
     (and (not item-in-cell)
          (item/valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/set-item id cell item-on-cursor]
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; STACK ITEMS
     (and item-in-cell
          (item/stackable? item-in-cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item id cell item-on-cursor]
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; SWAP ITEMS
     (and item-in-cell
          (item/valid-slot? cell item-on-cursor))
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
  (v-add player
         (v-scale (v-direction player target)
                  (min maxrange
                       (v-distance player target)))))

(defn- item-place-position [ctx entity*]
  (placement-point (:position entity*)
                   (world-mouse-position ctx)
                   ; so you cannot put it out of your own reach
                   (- (:entity/click-distance-tiles entity*) 0.1)))

(defn- world-item? [ctx]
  (not (mouse-on-actor? ctx)))

(defcomponent :player-item-on-cursor
  {:let {:keys [eid item]}}
  (->mk [[_ eid item] _ctx]
    {:eid eid
     :item item})

  (pause-game? [_]
    true)

  (manual-tick [_ ctx]
    (when (and (button-just-pressed? :left) (world-item? ctx))
      [[:tx/event eid :drop-item]]))

  (clicked-inventory-cell [_ cell]
    (clicked-cell @eid cell))

  (enter [_ _ctx]
    [[:tx/cursor :cursors/hand-grab]
     [:e/assoc eid :entity/item-on-cursor item]])

  (exit [_ ctx]
    ; at context.ui.inventory-window/clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (let [entity* @eid]
      (when (:entity/item-on-cursor entity*)
        [[:tx/sound "sounds/bfxr_itemputground.wav"]
         [:tx/item (item-place-position ctx entity*) (:entity/item-on-cursor entity*)]
         [:e/dissoc eid :entity/item-on-cursor]])))

  (render-below [_ entity* g ctx]
    (when (world-item? ctx)
      (draw-centered-image g (:entity/image item) (item-place-position ctx entity*)))))

(extend-type clojure.ctx.Graphics
  DrawItemOnCursor
  (draw-item-on-cursor [g ctx]
    (let [player-entity* (player-entity* ctx)]
      (when (and (= :player-item-on-cursor (entity-state player-entity*))
                 (not (world-item? ctx)))
        (draw-centered-image g
                             (:entity/image (:entity/item-on-cursor player-entity*))
                             (gui-mouse-position ctx))))))

(defcomponent :player-moving
  {:let {:keys [eid movement-vector]}}
  (->mk [[_ eid movement-vector] _ctx]
    {:eid eid
     :movement-vector movement-vector})

  (player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (pause-game? [_]
    false)

  (enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity-stat @eid :stats/movement-speed)}]])

  (exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (tick [_ eid _ctx]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity-stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))

(defcomponent :stunned
  {:let {:keys [eid counter]}}
  (->mk [[_ eid duration] ctx]
    {:eid eid
     :counter (->counter ctx duration)})

  (player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (pause-game? [_]
    false)

  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (render-below [_ entity* g _ctx]
    (draw-circle g (:position entity*) 0.5 [1 1 1 0.6])))

(def ^{:doc "Returns the player-entity atom." :private true} ctx-player :context/player-entity)

(defcomponent :entity/player?
  (create [_ eid ctx]
    (assoc ctx ctx-player eid)))

(defn- p-state-obj [ctx]
  (-> ctx player-entity* state-obj))

(extend-type clojure.ctx.Context
  Player
  (player-entity  [ctx]  (ctx-player ctx))
  (player-entity* [ctx] @(ctx-player ctx))
  (player-update-state      [ctx]       (manual-tick             (p-state-obj ctx) ctx))
  (player-state-pause-game? [ctx]       (pause-game?             (p-state-obj ctx)))
  (player-clicked-inventory [ctx cell]  (clicked-inventory-cell  (p-state-obj ctx) cell))
  (player-clicked-skillmenu [ctx skill] (clicked-skillmenu-skill (p-state-obj ctx) skill)))


