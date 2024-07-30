(ns cdq.entity.state
  (:require [reduce-fsm :as fsm]
            [core.component :as component]
            [cdq.api.context :refer [transact-all!]]
            [cdq.api.entity :as entity]
            [cdq.api.state :as state]))

(component/def :entity/state {}
  {:keys [initial-state
          fsm
          state-obj
          state-obj-constructors]}
  (entity/create-component [_ _components ctx]
    ; initial state is nil, so associng it.
    ; make bug report TODO
    {:fsm (assoc (fsm initial-state nil)  ; throws when initial-state is not part of states
                 :state initial-state)
     :state-obj ((initial-state state-obj-constructors) ctx nil)
     :state-obj-constructors state-obj-constructors})

  (entity/tick         [_ entity* ctx]   (state/tick         state-obj entity*   ctx))
  (entity/render-below [_ entity* g ctx] (state/render-below state-obj entity* g ctx))
  (entity/render-above [_ entity* g ctx] (state/render-above state-obj entity* g ctx))
  (entity/render-info  [_ entity* g ctx] (state/render-info  state-obj entity* g ctx)))

(extend-type cdq.api.entity.Entity
  entity/State
  (state [entity*]
    (-> entity* :entity/state :fsm :state))

  (state-obj [entity*]
    (-> entity* :entity/state :state-obj)))

(defn- send-event! [ctx entity event params]
  (when-let [{:keys [fsm
                     state-obj
                     state-obj-constructors]} (:entity/state @entity)]
    (let [old-state (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state (:state new-fsm)]
      (when (not= old-state new-state)
        (let [constructor (new-state state-obj-constructors)
              new-state-obj (if params
                              (constructor ctx @entity params)
                              (constructor ctx @entity))]
          (doseq [txs-fn [#(state/exit      state-obj @entity ctx)
                          #(state/enter new-state-obj @entity ctx)
                          #(if (:entity/player? @entity) (state/player-enter new-state-obj) [])
                          #(vector
                            [:tx/assoc-in entity [:entity/state :fsm] new-fsm]
                            [:tx/assoc-in entity [:entity/state :state-obj] new-state-obj])]]
            (transact-all! ctx (txs-fn))))))))

(defmethod cdq.api.context/transact! :tx/event [[_ entity event params] ctx]
  (send-event! ctx entity event params)
  [])
