(ns core.entity.state.player-idle
  (:require [utils.core :refer [safe-merge]]
            [utils.wasd-movement :refer [WASD-movement-vector]]
            [math.vector :as v]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.state :as state]
            [core.screens.stage :as stage]
            [core.effect.core :refer [->player-effect-ctx]]
            [core.mouseover-entity :as mouseover]
            [core.widgets :as widgets]
            [core.entity.state.active-skill :refer [skill-usable-state]]
            [gdx.scene2d.actor :refer [visible? toggle-visible! parent] :as actor]
            [gdx.scene2d.ui :as ui])
  (:import (com.badlogic.gdx Gdx Input$Buttons)))

(defn- denied [text]
  [[:tx/sound "sounds/bfxr_denied.wav"]
   [:tx/msg-to-player text]])

(defmulti ^:private on-clicked
  (fn [_context entity*]
    (:type (:entity/clickable entity*))))

(defmethod on-clicked :clickable/item
  [context clicked-entity*]
  (let [player-entity* (player/entity* context)
        item (:entity/item clicked-entity*)
        clicked-entity (:entity/id clicked-entity*)]
    (cond
     (visible? (widgets/inventory-window context))
     [[:tx/sound "sounds/bfxr_takeit.wav"]
      [:tx/destroy clicked-entity]
      [:tx/event (:entity/id player-entity*) :pickup-item item]]

     (entity/can-pickup-item? player-entity* item)
     [[:tx/sound "sounds/bfxr_pickup.wav"]
      [:tx/destroy clicked-entity]
      [:tx/pickup-item (:entity/id player-entity*) item]]

     :else
     [[:tx/sound "sounds/bfxr_denied.wav"]
      [:tx/msg-to-player "Your Inventory is full"]])))

(defmethod on-clicked :clickable/player
  [ctx _clicked-entity*]
  (toggle-visible! (widgets/inventory-window ctx))) ; TODO no tx

(defn- clickable->cursor [mouseover-entity* too-far-away?]
  (case (:type (:entity/clickable mouseover-entity*))
    :clickable/item (if too-far-away?
                      :cursors/hand-before-grab-gray
                      :cursors/hand-before-grab)
    :clickable/player :cursors/bag))

(defn- ->clickable-mouseover-entity-interaction [ctx player-entity* mouseover-entity*]
  (if (< (v/distance (:position player-entity*) (:position mouseover-entity*))
         (:entity/click-distance-tiles player-entity*))
    [(clickable->cursor mouseover-entity* false) (fn [] (on-clicked ctx mouseover-entity*))]
    [(clickable->cursor mouseover-entity* true)  (fn [] (denied "Too far away"))]))

; TODO move to inventory-window extend Context
(defn- inventory-cell-with-item? [ctx actor]
  (and (parent actor)
       (= "inventory-cell" (actor/name (parent actor)))
       (get-in (:entity/inventory (player/entity* ctx))
               (actor/id (parent actor)))))

(defn- mouseover-actor->cursor [ctx]
  (let [actor (stage/mouse-on-actor? ctx)]
    (cond
     (inventory-cell-with-item? ctx actor) :cursors/hand-before-grab
     (ui/window-title-bar? actor) :cursors/move-window
     (ui/button? actor) :cursors/over-button
     :else :cursors/default)))

(defn- ->interaction-state [context entity*]
  (let [mouseover-entity* (mouseover/entity* context)]
    (cond
     (stage/mouse-on-actor? context)
     [(mouseover-actor->cursor context)
      (fn []
        nil)] ; handled by actors themself, they check player state

     (and mouseover-entity*
          (:entity/clickable mouseover-entity*))
     (->clickable-mouseover-entity-interaction context entity* mouseover-entity*)

     :else
     (if-let [skill-id (widgets/selected-skill context)]
       (let [skill (skill-id (:entity/skills entity*))
             effect-ctx (->player-effect-ctx context entity*)
             state (skill-usable-state (safe-merge context effect-ctx) entity* skill)]
         (if (= state :usable)
           (do
            ; TODO cursor AS OF SKILL effect (SWORD !) / show already what the effect would do ? e.g. if it would kill highlight
            ; different color ?
            ; => e.g. meditation no TARGET .. etc.
            [:cursors/use-skill
             (fn []
               [[:tx/event (:entity/id entity*) :start-action [skill effect-ctx]]])])
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
        [[:tx/assoc eid :entity/free-skill-points (dec free-skill-points)]
         [:tx/add-skill eid skill]]))))
