(ns entity-state.fsms
  (:require [reduce-fsm :as fsm]
            (entity-state [active-skill :as active-skill]
                          [stunned :as stunned]
                          [npc-dead :as npc-dead]
                          [npc-idle :as npc-idle]
                          [npc-moving :as npc-moving]
                          [npc-sleeping :as npc-sleeping]
                          [player-dead :as player-dead]
                          [player-found-princess :as found-princess]
                          [player-idle :as player-idle]
                          [player-item-on-cursor :as item-on-cursor]
                          [player-moving :as player-moving])
            [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

(fsm/defsm-inc ^:private player-fsm
  [[:idle
    :kill -> :dead
    :stun -> :stunned
    :start-action -> :active-skill
    :pickup-item -> :item-on-cursor
    :movement-input -> :moving
    :found-princess -> :princess-saved]
   [:moving
    :kill -> :dead
    :stun -> :stunned
    :no-movement-input -> :idle]
   [:active-skill
    :kill -> :dead
    :stun -> :stunned
    :action-done -> :idle]
   [:stunned
    :kill -> :dead
    :effect-wears-off -> :idle]
   [:item-on-cursor
    :kill -> :dead
    :stun -> :stunned
    :drop-item -> :idle
    :dropped-item -> :idle]
   [:princess-saved]
   [:dead]])

(def ^:private player-state-obj-constructors
  {:item-on-cursor (fn [_ctx _entity* item] (item-on-cursor/->PlayerItemOnCursor item))
   :idle           (constantly (player-idle/->PlayerIdle))
   :moving         (fn [_ctx _entity* v] (player-moving/->PlayerMoving v))
   :active-skill   active-skill/->CreateWithCounter
   :stunned        stunned/->CreateWithCounter
   :dead           (constantly (player-dead/->PlayerDead))
   :princess-saved (constantly (found-princess/->PlayerFoundPrincess))})

(defn ->player-state [initial-state]
  {:initial-state initial-state
   :fsm player-fsm
   :state-obj-constructors player-state-obj-constructors})

(fsm/defsm-inc ^:private npc-fsm
  [[:sleeping
    :kill -> :dead
    :stun -> :stunned
    :alert -> :idle]
   [:idle
    :kill -> :dead
    :stun -> :stunned
    :start-action -> :active-skill
    :movement-direction -> :moving]
   [:moving
    :kill -> :dead
    :stun -> :stunned
    :timer-finished -> :idle]
   [:active-skill
    :kill -> :dead
    :stun -> :stunned
    :action-done -> :idle]
   [:stunned
    :kill -> :dead
    :effect-wears-off -> :idle]
   [:dead]])

(def ^:private npc-state-obj-constructors
  {:sleeping     (constantly (npc-sleeping/->NpcSleeping))
   :idle         (constantly (npc-idle/->NpcIdle))
   :moving       npc-moving/->npc-moving
   :active-skill active-skill/->CreateWithCounter
   :stunned      stunned/->CreateWithCounter
   :dead         (constantly (npc-dead/->NpcDead))})

(defn ->npc-state [initial-state]
  {:initial-state initial-state
   :fsm npc-fsm
   :state-obj-constructors npc-state-obj-constructors})

(defcomponent :effect/stun data/pos-attr
  (effect/text [[_ duration] _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/usable? [_ {:keys [effect/target]}]
    (and target
         (:entity/state @target)))

  (transact! [[_ duration] {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))

(defcomponent :effect/kill data/boolean-attr
  (effect/text [_ _effect-ctx]
    "Kills target")

  (effect/usable? [_ {:keys [effect/source effect/target]}]
    (and target
         (:entity/state @target)))

  (transact! [_ {:keys [effect/target]}]
    [[:tx/event target :kill]]))
