(ns core.entity.state.components
  (:require [core.utils.core :refer [safe-merge]]
            [core.utils.wasd-movement :refer [WASD-movement-vector]]
            [core.math.vector :as v]
            [core.component :as component :refer [defcomponent]]
            [core.screens.stage :as stage]
            [core.ctx.screens :as screens]
            [core.ctx.grid :as grid]
            [core.ctx.potential-fields :as potential-fields]
            [core.ctx.time :as time]
            [core.graphics.views :refer [world-mouse-position gui-mouse-position]]
            [core.effect.core :refer [->npc-effect-ctx skill-usable-state applicable?]]
            [core.entity :as entity]
            [core.entity.state :as state]
            [core.entity.inventory :as inventory]
            [core.entity.player :as player]
            [core.player.interaction-state :refer [->interaction-state]]
            [core.graphics :as g]
            [core.effect :as effect])
  (:import (com.badlogic.gdx Gdx Input$Buttons)))

(defn- draw-skill-icon [g icon entity* [x y] action-counter-ratio]
  (let [[width height] (:world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ (float width) 2)
        y (+ (float y) (float (:half-height entity*)) (float 0.15))
        center [x (+ y radius)]]
    (g/draw-filled-circle g center radius [1 1 1 0.125])
    (g/draw-sector g center radius
                   90 ; start-angle
                   (* (float action-counter-ratio) 360) ; degree
                   [1 1 1 0.5])
    (g/draw-image g icon [(- (float x) radius) y])))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (entity/stat entity* (:skill/action-time-modifier-key skill))
         1)))

(defcomponent :active-skill
  {:let {:keys [eid skill effect-ctx counter]}}
  (component/create [[_ eid [skill effect-ctx]] ctx]
    {:eid eid
     :skill skill
     :effect-ctx effect-ctx
     :counter (->> skill
                   :skill/action-time
                   (apply-action-speed-modifier @eid skill)
                   (time/->counter ctx))})

  (state/player-enter [_]
    [[:tx/cursor :cursors/sandclock]])

  (state/pause-game? [_]
    false)

  (state/enter [_ ctx]
    [[:tx/sound (:skill/start-action-sound skill)]
     (when (:skill/cooldown skill)
       [:e/assoc-in eid [:entity/skills (:property/id skill) :skill/cooling-down?] (time/->counter ctx (:skill/cooldown skill))])
     (when-not (zero? (:skill/cost skill))
       [:tx.entity.stats/pay-mana-cost eid (:skill/cost skill)])])

  (entity/tick [_ eid context]
    (cond
     (not (applicable? (safe-merge context effect-ctx) (:skill/effects skill)))
     [[:tx/event eid :action-done]
      ; TODO some sound ?
      ]

     (time/stopped? context counter)
     [[:tx/event eid :action-done]
      [:tx/effect effect-ctx (:skill/effects skill)]]))

  (entity/render-info [_ entity* g ctx]
    (let [{:keys [entity/image skill/effects]} skill]
      (draw-skill-icon g image entity* (:position entity*) (time/finished-ratio ctx counter))
      (run! #(component/render % g (merge ctx effect-ctx)) effects))))

(defcomponent :npc-dead
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/enter [_ _ctx]
    [[:e/destroy eid]]))

; TODO
; split it into 3 parts
; applicable
; useful
; usable?
(defn- useful? [ctx effects]
  ;(println "Check useful? for effects: " (map first effects))
  (let [applicable-effects (filter #(component/applicable? % ctx) effects)
        ;_ (println "applicable-effects: " (map first applicable-effects))
        useful-effect (some #(component/useful? % ctx) applicable-effects)]
    ;(println "Useful: " useful-effect)
    useful-effect))

(defn- npc-choose-skill [ctx entity*]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill-usable-state ctx entity* %))
                     (useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/state
       entity* @(core.ctx.ecs/get-entity ctx uid)
       effect-ctx (->npc-effect-ctx ctx entity*)]
   (npc-choose-skill (safe-merge ctx effect-ctx) entity*))
 )

(defcomponent :npc-idle
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (entity/tick [_ eid ctx]
    (let [entity* @eid
          effect-ctx (->npc-effect-ctx ctx entity*)]
      (if-let [skill (npc-choose-skill (safe-merge ctx effect-ctx) entity*)]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (potential-fields/follow-to-enemy ctx eid)]]))))

; npc moving is basically a performance optimization so npcs do not have to check
; pathfinding/usable skills every frame
; also prevents fast twitching around changing directions every frame
(defcomponent :npc-moving
  {:let {:keys [eid movement-vector counter]}}
  (component/create [[_ eid movement-vector] ctx]
    {:eid eid
     :movement-vector movement-vector
     :counter (time/->counter ctx (* (entity/stat @eid :stats/reaction-time) 0.016))})

  (state/enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (or (entity/stat @eid :stats/movement-speed) 0)}]])

  (state/exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      [[:tx/event eid :timer-finished]])))

(defcomponent :npc-sleeping
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/exit [_ ctx]
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (entity/tick [_ eid context]
    (let [entity* @eid
          cell ((:context/grid context) (entity/tile entity*))]
      (when-let [distance (grid/nearest-entity-distance @cell (entity/enemy-faction entity*))]
        (when (<= distance (entity/stat entity* :stats/aggro-range))
          [[:tx/event eid :alert]]))))

  (entity/render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true}))))

(defcomponent :player-dead
  (state/player-enter [_]
    [[:tx/cursor :cursors/black-x]])

  (state/pause-game? [_]
    true)

  (state/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(screens/change-screen % :screens/main-menu)}]]))

(defcomponent :player-idle
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/pause-game? [_]
    true)

  (state/manual-tick [_ ctx]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/event eid :movement-input movement-vector]]
      (let [[cursor on-click] (->interaction-state ctx @eid)]
        (cons [:tx/cursor cursor]
              (when (.isButtonJustPressed Gdx/input Input$Buttons/LEFT)
                (on-click))))))

  (state/clicked-inventory-cell [_ cell]
    ; TODO no else case
    (when-let [item (get-in (:entity/inventory @eid) cell)]
      [[:tx/sound "sounds/bfxr_takeit.wav"]
       [:tx/event eid :pickup-item item]
       [:tx/remove-item eid cell]]))

  (state/clicked-skillmenu-skill [_ skill]
    (let [free-skill-points (:entity/free-skill-points @eid)]
      ; TODO no else case, no visible free-skill-points
      (when (and (pos? free-skill-points)
                 (not (entity/has-skill? @eid skill)))
        [[:e/assoc eid :entity/free-skill-points (dec free-skill-points)]
         [:tx/add-skill eid skill]]))))

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
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; STACK ITEMS
     (and item (inventory/stackable? item item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item id cell item-on-cursor]
      [:e/dissoc id :entity/item-on-cursor]
      [:tx/event id :dropped-item]]

     ; SWAP ITEMS
     (and item
          (inventory/valid-slot? cell item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/remove-item id cell]
      [:tx/set-item id cell item-on-cursor]
      ; need to dissoc and drop otherwise state enter does not trigger picking it up again
      ; TODO? coud handle pickup-item from item-on-cursor state also
      [:e/dissoc id :entity/item-on-cursor]
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
  (placement-point (:position entity*)
                   (world-mouse-position ctx)
                   ; so you cannot put it out of your own reach
                   (- (:entity/click-distance-tiles entity*) 0.1)))

(defn- world-item? [ctx]
  (not (stage/mouse-on-actor? ctx)))

(defcomponent :player-item-on-cursor
  {:let {:keys [eid item]}}
  (component/create [[_ eid item] _ctx]
    {:eid eid
     :item item})

  (state/pause-game? [_]
    true)

  (state/manual-tick [_ ctx]
    (when (and (.isButtonJustPressed Gdx/input Input$Buttons/LEFT)
               (world-item? ctx))
      [[:tx/event eid :drop-item]]))

  (state/clicked-inventory-cell [_ cell]
    (clicked-cell @eid cell))

  (state/enter [_ _ctx]
    [[:tx/cursor :cursors/hand-grab]
     [:e/assoc eid :entity/item-on-cursor item]])

  (state/exit [_ ctx]
    ; at context.ui.inventory-window/clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (let [entity* @eid]
      (when (:entity/item-on-cursor entity*)
        [[:tx/sound "sounds/bfxr_itemputground.wav"]
         [:tx/item (item-place-position ctx entity*) (:entity/item-on-cursor entity*)]
         [:e/dissoc eid :entity/item-on-cursor]])))

  (entity/render-below [_ entity* g ctx]
    (when (world-item? ctx)
      (g/draw-centered-image g (:entity/image item) (item-place-position ctx entity*)))))

(defn draw-item-on-cursor [g ctx]
  (let [player-entity* (player/entity* ctx)]
    (when (and (= :player-item-on-cursor (entity/state player-entity*))
               (not (world-item? ctx)))
      (g/draw-centered-image g
                             (:entity/image (:entity/item-on-cursor player-entity*))
                             (gui-mouse-position ctx)))))

(defcomponent :player-moving
  {:let {:keys [eid movement-vector]}}
  (component/create [[_ eid movement-vector] _ctx]
    {:eid eid
     :movement-vector movement-vector})

  (state/player-enter [_]
    [[:tx/cursor :cursors/walking]])

  (state/pause-game? [_]
    false)

  (state/enter [_ _ctx]
    [[:tx/set-movement eid {:direction movement-vector
                            :speed (entity/stat @eid :stats/movement-speed)}]])

  (state/exit [_ _ctx]
    [[:tx/set-movement eid nil]])

  (entity/tick [_ eid context]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/set-movement eid {:direction movement-vector
                              :speed (entity/stat @eid :stats/movement-speed)}]]
      [[:tx/event eid :no-movement-input]])))

(defcomponent :stunned
  {:let {:keys [eid counter]}}
  (component/create [[_ eid duration] ctx]
    {:eid eid
     :counter (time/->counter ctx duration)})

  (state/player-enter [_]
    [[:tx/cursor :cursors/denied]])

  (state/pause-game? [_]
    false)

  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (entity/render-below [_ entity* g _ctx]
    (g/draw-circle g (:position entity*) 0.5 [1 1 1 0.6])))
