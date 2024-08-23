(ns components.entity-state.fsms
  (:require [reduce-fsm :as fsm]
            [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [core.effect :as effect]
            ; cannot load @ app.edn as no namespaced kws
            (components.entity-state active-skill
                                     stunned
                                     npc-dead
                                     npc-idle
                                     npc-moving
                                     npc-sleeping
                                     player-dead
                                     player-found-princess
                                     player-idle
                                     player-item-on-cursor
                                     player-moving)))

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
    :movement-input -> :player-moving
    :found-princess -> :princess-saved]
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
   [:princess-saved]
   [:player-dead]])

(defn ->player-state [initial-state]
  {:initial-state initial-state
   :fsm player-fsm})

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

(defn ->npc-state [initial-state]
  {:initial-state initial-state
   :fsm npc-fsm})

(defcomponent :effect/stun data/pos-attr
  duration
  (effect/text [_ _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/applicable? [_ {:keys [effect/target]}]
    (and target
         (:entity/state @target)))

  (effect/do! [_ {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))

(defcomponent :effect/kill data/boolean-attr
  (effect/text [_ _effect-ctx]
    "Kills target")

  (effect/applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (:entity/state @target)))

  (effect/do! [_ {:keys [effect/target]}]
    [[:tx/event target :kill]]))
