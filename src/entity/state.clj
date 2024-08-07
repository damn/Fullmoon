(ns entity.state
  (:require [reduce-fsm :as fsm]
            [core.component :refer [defcomponent]]
            [api.context :refer [transact-all!]]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.tx :refer [transact!]]))

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

  (entity/tick         [[_ {:keys [state-obj]}] entity* ctx]   (state/tick         state-obj entity*   ctx))
  (entity/render-below [[_ {:keys [state-obj]}] entity* g ctx] (state/render-below state-obj entity* g ctx))
  (entity/render-above [[_ {:keys [state-obj]}] entity* g ctx] (state/render-above state-obj entity* g ctx))
  (entity/render-info  [[_ {:keys [state-obj]}] entity* g ctx] (state/render-info  state-obj entity* g ctx)))

(extend-type api.entity.Entity
  entity/State
  (state [entity*]
    (-> entity* :entity/state :fsm :state))

  (state-obj [entity*]
    (-> entity* :entity/state :state-obj)))

(defn- send-event! [ctx entity event params]
  (if-let [{:keys [fsm
                   state-obj
                   state-obj-constructors]} (:entity/state @entity)]
    (let [old-state (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state (:state new-fsm)]
      (if (= old-state new-state)
        ctx
        (let [constructor (new-state state-obj-constructors)
              new-state-obj (if params
                              (constructor ctx @entity params)
                              (constructor ctx @entity))]
          (reduce (fn [ctx txs-fn]
                    (transact-all! ctx (txs-fn))
                    )
                  ctx
                  [#(state/exit      state-obj @entity ctx)
                   #(state/enter new-state-obj @entity ctx)
                   #(if (:entity/player? @entity) (state/player-enter new-state-obj) [])
                   #(vector
                     [:tx.entity/assoc-in entity [:entity/state :fsm] new-fsm]
                     [:tx.entity/assoc-in entity [:entity/state :state-obj] new-state-obj])]
                  ))))
    ctx))

(defmethod transact! :tx/event [[_ entity event params] ctx]
  (send-event! ctx entity event params))
