(ns components.entity.state
  (:require [reduce-fsm :as fsm]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.entity-state :as state]))

; fsm throws when initial-state is not part of states, so no need to assert initial-state
; initial state is nil, so associng it. make bug report at reduce-fsm?
(defn- ->init-fsm [fsm initial-state]
  (assoc (fsm initial-state nil) :state initial-state))

(defcomponent :entity/state
  (entity/create [[k {:keys [fsm initial-state]}] eid ctx]
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
          [#(state/exit old-state-obj %)
           #(state/enter new-state-obj %)
           (if (:entity/player? @eid)
             (fn [_ctx] (state/player-enter new-state-obj)))
           [:tx.entity/assoc eid :entity/state new-fsm]
           [:tx.entity/dissoc eid old-state-k]
           [:tx.entity/assoc eid new-state-k (new-state-obj 1)]])))))

(defcomponent :tx/event
  (effect/do! [[_ eid event params] ctx]
    (send-event! ctx eid event params)))
