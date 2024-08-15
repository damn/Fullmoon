(ns entity.state
  (:require [reduce-fsm :as fsm]
            [core.component :refer [defcomponent]]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.tx :refer [transact!]]))

(defn- state-key [state]
  (-> state :fsm :state))

(defcomponent :entity/state {}
  (entity/create-component [[_ {:keys [fsm initial-state state-obj-constructors]}]
                            _components
                            ctx]
    ; initial state is nil, so associng it.
    ; make bug report TODO
    {:fsm (assoc (fsm initial-state nil)  ; throws when initial-state is not part of states
                 :state initial-state)
     :state-obj ((initial-state state-obj-constructors) ctx nil)
     :state-obj-constructors state-obj-constructors})

  (entity/info-text [[_ state] _ctx]
    (str "[YELLOW]State: " (name (state-key state)) "[]"))
  ; TODO also info, e.g. active-skill -> which skill ....
  ; not just 'active-skill'
  ; => again a component ... ?

  (entity/tick         [[_ {:keys [state-obj]}] entity* ctx]   (state/tick         state-obj entity*   ctx))
  (entity/render-below [[_ {:keys [state-obj]}] entity* g ctx] (state/render-below state-obj entity* g ctx))
  (entity/render-above [[_ {:keys [state-obj]}] entity* g ctx] (state/render-above state-obj entity* g ctx))
  (entity/render-info  [[_ {:keys [state-obj]}] entity* g ctx] (state/render-info  state-obj entity* g ctx)))

(extend-type api.entity.Entity
  entity/State
  (state [entity*]
    (-> entity* :entity/state state-key))

  (state-obj [entity*]
    (-> entity* :entity/state :state-obj)))

(defmethod transact! ::exit [[_ state-obj entity] ctx]
  (state/exit state-obj @entity ctx))

(defmethod transact! ::enter [[_ state-obj entity] ctx]
  (state/enter state-obj @entity ctx))

(defmethod transact! ::player-enter [[_ state-obj] ctx]
  (state/player-enter state-obj))

(defn- send-event! [ctx entity event params]
  (if-let [{:keys [fsm
                   state-obj
                   state-obj-constructors]} (:entity/state @entity)]
    (let [old-state (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state (:state new-fsm)]
      (if-not (= old-state new-state)
        (let [constructor (new-state state-obj-constructors)
              new-state-obj (if params
                              (constructor ctx @entity params)
                              (constructor ctx @entity))]
          [[::exit state-obj entity]
           [::enter new-state-obj entity]
           (if (:entity/player? @entity)
             [::player-enter new-state-obj])
           [:tx.entity/assoc-in entity [:entity/state :fsm] new-fsm]
           [:tx.entity/assoc-in entity [:entity/state :state-obj] new-state-obj]])))))

(comment
 (require '[entity-state.npc :as npc-state])
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
   )

 )

(defmethod transact! :tx/event [[_ entity event params] ctx]
  (send-event! ctx entity event params))
