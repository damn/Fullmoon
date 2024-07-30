(ns cdq.state.player-idle
  (:require [gdl.context :as ctx :refer [mouse-on-stage-actor? button-just-pressed? button-pressed?]]
            [gdl.graphics :as g]
            [gdl.input.buttons :as buttons]
            [gdl.scene2d.actor :refer [visible? toggle-visible! parent] :as actor]
            [gdl.scene2d.ui.button :refer [button?]]
            [gdl.scene2d.ui.window :refer [window-title-bar?]]
            [gdl.math.vector :as v]
            [utils.wasd-movement :refer [WASD-movement-vector]]
            [cdq.api.context :refer [get-property inventory-window skill-usable-state selected-skill]]
            [cdq.api.entity :as entity]
            [cdq.api.state :as state]))

(defn- denied [text]
  [[:tx/sound "sounds/bfxr_denied.wav"]
   [:tx/msg-to-player text]])

(defmulti ^:private on-clicked
  (fn [_context entity*]
    (:type (:entity/clickable entity*))))

(defmethod on-clicked :clickable/item
  [{:keys [context/player-entity] :as context} clicked-entity*]
  (let [item (:entity/item clicked-entity*)
        clicked-entity (:entity/id clicked-entity*)]
    (cond
     (visible? (inventory-window context))
     [[:tx/sound "sounds/bfxr_takeit.wav"]
      [:tx/destroy clicked-entity]
      [:tx/event player-entity :pickup-item item]]

     (entity/can-pickup-item? @player-entity item)
     [[:tx/sound "sounds/bfxr_pickup.wav"]
      [:tx/destroy clicked-entity]
      [:tx/pickup-item player-entity item]]

     :else
     [[:tx/sound "sounds/bfxr_denied.wav"]
      [:tx/msg-to-player "Your Inventory is full"]])))

(defmethod on-clicked :clickable/player
  [ctx _clicked-entity*]
  (toggle-visible! (inventory-window ctx))) ; _ TODO _

(defmethod on-clicked :clickable/princess
  [ctx _clicked-entity*]
  [[:tx/event (:context/player-entity ctx) :found-princess]])

(defn- clickable->cursor [mouseover-entity* too-far-away?]
  (case (:type (:entity/clickable mouseover-entity*))
    :clickable/item (if too-far-away?
                      :cursors/hand-before-grab-gray
                      :cursors/hand-before-grab)
    :clickable/player :cursors/bag
    :clickable/princess (if too-far-away?
                          :cursors/princess-gray
                          :cursors/princess)))

(defn- ->clickable-mouseover-entity-interaction [ctx player-entity* mouseover-entity*]
  (if (and (< (v/distance (:entity/position player-entity*)
                          (:entity/position mouseover-entity*))
              (:entity/click-distance-tiles player-entity*)))
    [(clickable->cursor mouseover-entity* false) (fn [] (on-clicked ctx mouseover-entity*))]
    [(clickable->cursor mouseover-entity* true)  (fn [] (denied "Too far away"))]))

(defn- effect-context [{:keys [context/mouseover-entity] :as ctx}
                       entity*]
  (let [target @mouseover-entity
        target-position (or (and target (:entity/position @target))
                            (ctx/world-mouse-position ctx))]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/target-position target-position
     :effect/direction (v/direction (:entity/position entity*) target-position)}))

; TODO move to inventory-window extend Context
(defn- inventory-cell-with-item? [{:keys [context/player-entity]} actor]
  (and (parent actor)
       (= "inventory-cell" (actor/name (parent actor)))
       (get-in (:entity/inventory @player-entity)
               (actor/id (parent actor)))))

(defn- mouseover-actor->cursor [ctx]
  (let [actor (mouse-on-stage-actor? ctx)]
    (cond
     (inventory-cell-with-item? ctx actor) :cursors/hand-before-grab
     (window-title-bar? actor) :cursors/move-window
     (button? actor) :cursors/over-button
     :else :cursors/default)))

(defn- ->interaction-state [{:keys [context/mouseover-entity] :as context} entity*]
  (cond
   (mouse-on-stage-actor? context)
   [(mouseover-actor->cursor context)
    (fn []
      nil)] ; handled by actors themself, they check player state

   (and @mouseover-entity
        (:entity/clickable @@mouseover-entity))
   (->clickable-mouseover-entity-interaction context entity* @@mouseover-entity)

   :else
   (if-let [skill-id (selected-skill context)]
     (let [effect-context (effect-context context entity*)
           skill (skill-id (:entity/skills entity*))
           state (skill-usable-state (merge context effect-context) entity* skill)]
       (if (= state :usable)
         (do
          ; TODO cursor AS OF SKILL effect (SWORD !) / show already what the effect would do ? e.g. if it would kill highlight
          ; different color ?
          ; => e.g. meditation no TARGET .. etc.
          [:cursors/use-skill
           (fn []
             [[:tx/event (:entity/id entity*) :start-action [skill effect-context]]])])
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
      (fn [] (denied "No selected skill"))])))

(defrecord PlayerIdle []
  state/PlayerState
  (player-enter [_])
  (pause-game? [_] true)
  (manual-tick [_ entity* context]
    (if-let [movement-vector (WASD-movement-vector context)]
      [[:tx/event (:entity/id entity*) :movement-input movement-vector]]
      (let [[cursor on-click] (->interaction-state context entity*)]
        (cons [:tx/cursor cursor]
              (when (button-just-pressed? context buttons/left)
                (on-click))))))

  (clicked-inventory-cell [_ {:keys [entity/id entity/inventory]} cell]
    (when-let [item (get-in inventory cell)]
      [[:tx/sound "sounds/bfxr_takeit.wav"]
       [:tx/event id :pickup-item item]
       [:tx/remove-item id cell]]))

  (clicked-skillmenu-skill [_ {:keys [entity/id entity/free-skill-points] :as entity*} skill]
    (when (and (pos? free-skill-points)
               (not (entity/has-skill? entity* skill)))
      [[:tx/assoc id :entity/free-skill-points (dec free-skill-points)]
       [:tx/add-skill id skill]]))
  ; TODO no else case, no visible fsp..

  state/State
  (enter [_ entity* _ctx])
  (exit  [_ entity* context])
  (tick [_ entity* _context])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
