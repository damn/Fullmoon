(ns cdq.state.npc
  (:require [reduce-fsm :as fsm]
            (cdq.state [active-skill :as active-skill]
                       [npc-dead :as dead]
                       [npc-idle :as idle]
                       [npc-moving :as moving]
                       [npc-sleeping :as sleeping]
                       [stunned :as stunned])))

(fsm/defsm-inc ^:private fsm
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

(def ^:private state-obj-constructors
  {:sleeping     (constantly (sleeping/->NpcSleeping))
   :idle         (constantly (idle/->NpcIdle))
   :moving       moving/->npc-moving
   :active-skill active-skill/->CreateWithCounter
   :stunned      stunned/->CreateWithCounter
   :dead         (constantly (dead/->NpcDead))})

(defn ->state [initial-state]
  {:initial-state initial-state
   :fsm fsm
   :state-obj-constructors state-obj-constructors})
