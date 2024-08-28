(ns components.entity.state
  (:require [reduce-fsm :as fsm]
            [utils.core :refer [readable-number]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]))

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

(defcomponent :effect.entity/stun
  {:data :pos
   :let duration}
  (component/info-text [_ _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (component/applicable? [_ {:keys [effect/target]}]
    (and target
         (:entity/state @target)))

  (component/do! [_ {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))

(defcomponent :effect.entity/kill
  {:data :some}
  (component/info-text [_ _effect-ctx]
    "Kills target")

  (component/applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (:entity/state @target)))

  (component/do! [_ {:keys [effect/target]}]
    [[:tx/event target :kill]]))


; fsm throws when initial-state is not part of states, so no need to assert initial-state
; initial state is nil, so associng it. make bug report at reduce-fsm?
(defn- ->init-fsm [fsm initial-state]
  (assoc (fsm initial-state nil) :state initial-state))

(defcomponent :entity/state
  (component/create [[_ [player-or-npc initial-state]] _ctx]
    {:initial-state initial-state
     :fsm (case player-or-npc
            :state/player player-fsm
            :state/npc npc-fsm)})

  (component/create-e [[k {:keys [fsm initial-state]}] eid ctx]
    [[:tx.entity/assoc eid k (->init-fsm fsm initial-state)]
     [:tx.entity/assoc eid initial-state (component/create [initial-state eid] ctx)]])

  (component/info-text [[_ fsm] _ctx]
    (str "[YELLOW]State: " (name (:state fsm)) "[]")))

(extend-type core.entity.Entity
  entity/State
  (state [entity*]
    (-> entity* :entity/state :state))

  (state-obj [entity*]
    (let [state-k (entity/state entity*)]
      [state-k (state-k entity*)])))

(defn- send-event! [ctx eid event params]
  (when-let [fsm (:entity/state @eid)]
    (let [old-state-k (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state-k (:state new-fsm)]
      (when-not (= old-state-k new-state-k)
        (let [old-state-obj (entity/state-obj @eid)
              new-state-obj [new-state-k (component/create [new-state-k eid params] ctx)]]
          [#(component/exit old-state-obj %)
           #(component/enter new-state-obj %)
           (if (:entity/player? @eid)
             (fn [_ctx] (component/player-enter new-state-obj)))
           [:tx.entity/assoc eid :entity/state new-fsm]
           [:tx.entity/dissoc eid old-state-k]
           [:tx.entity/assoc eid new-state-k (new-state-obj 1)]])))))

(defcomponent :tx/event
  (component/do! [[_ eid event params] ctx]
    (send-event! ctx eid event params)))
