(ns cdq.state.player
  (:require [reduce-fsm :as fsm]
            (cdq.state [active-skill :as active-skill]
                       [player-dead :as dead]
                       [player-found-princess :as found-princess]
                       [player-idle :as idle]
                       [player-item-on-cursor :as item-on-cursor]
                       [player-moving :as moving]
                       [stunned :as stunned])))

(fsm/defsm-inc ^:private fsm
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

(def ^:private state-obj-constructors
  {:item-on-cursor (fn [_ctx _entity* item] (item-on-cursor/->PlayerItemOnCursor item))
   :idle           (constantly (idle/->PlayerIdle))
   :moving         (fn [_ctx _entity* v] (moving/->PlayerMoving v))
   :active-skill   active-skill/->CreateWithCounter
   :stunned        stunned/->CreateWithCounter
   :dead           (constantly (dead/->PlayerDead))
   :princess-saved (constantly (found-princess/->PlayerFoundPrincess))})

(defn ->state [initial-state]
  {:initial-state initial-state
   :fsm fsm
   :state-obj-constructors state-obj-constructors})
