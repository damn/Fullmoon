(ns components.entity.state
  (:require [reduce-fsm :as fsm]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.entity-state :as state]))

(defn- state-key [state]
  (-> state :fsm :state))

(defcomponent :entity/state {}
  {:keys [state-obj]}
  (entity/create [[k {:keys [fsm initial-state state-obj-constructors]}] eid ctx]
    ; fsm throws when initial-state is not part of states, so no need to assert initial-state
    [[:tx.entity/assoc eid k {:fsm (assoc (fsm initial-state nil)
                                          :state initial-state) ; initial state is nil, so associng it. make bug report TODO
                              :state-obj ((initial-state state-obj-constructors) ctx eid nil)
                              :state-obj-constructors state-obj-constructors}]])

  (component/info-text [[_ state] _ctx]
    ; TODO also info, e.g. active-skill -> which skill ....
    ; not just 'active-skill'
    ; => again a component ... ?
    (str "[YELLOW]State: " (name (state-key state)) "[]"))

  (entity/tick [_ _eid ctx]
    (state/tick state-obj ctx))

  (entity/render-below [_ entity* g ctx] (state/render-below state-obj entity* g ctx))
  (entity/render-above [_ entity* g ctx] (state/render-above state-obj entity* g ctx))
  (entity/render-info  [_ entity* g ctx] (state/render-info  state-obj entity* g ctx)))

(extend-type core.entity.Entity
  entity/State
  (state [entity*]
    (-> entity* :entity/state state-key))

  (state-obj [entity*]
    (-> entity* :entity/state :state-obj)))

(defn- send-event! [ctx eid event params]
  (if-let [{:keys [fsm
                   state-obj
                   state-obj-constructors]} (:entity/state @eid)]
    (let [old-state (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state (:state new-fsm)]
      (if-not (= old-state new-state)
        (let [constructor (new-state state-obj-constructors)
              new-state-obj (constructor ctx eid params)]
          [#(state/exit state-obj %)
           #(state/enter new-state-obj %)
           (if (:entity/player? @eid) (fn [_ctx] (state/player-enter new-state-obj)))
           [:tx.entity/assoc-in eid [:entity/state :fsm] new-fsm]
           [:tx.entity/assoc-in eid [:entity/state :state-obj] new-state-obj]])))))

(comment
 (require '[entity-state.fsms :as npc-state])
 ; choose a initial state constructor w/o needing ctx
 ; and new state also no ctx
 (let [initial-state :sleeping
       state (npc-state/->state initial-state)
       components nil
       ctx nil
       entity (atom {:entity/player? true
                     :entity/state (entity/create-component [:entity/state (npc-state/->state initial-state)]
                                                            components
                                                            ctx)})
       event :alert
       params nil
       ]
   (send-event! ctx entity event params)
   ; TODO create fsm picture
   ; TODO why we pass entity and not entity* ?? would be simpler ???
   ))

(defcomponent :tx/event {}
  (effect/do! [[_ eid event params] ctx]
    (send-event! ctx eid event params)))
