(ns world.entity.state
  (:require [core.component :refer [defsystem defc]]
            [core.info :as info]
            [core.tx :as tx]
            [reduce-fsm :as fsm]
            [world.entity :as entity]))

(defsystem enter)
(defmethod enter :default [_])

(defsystem exit)
(defmethod exit :default [_])

(defsystem player-enter)
(defmethod player-enter :default [_])

(defsystem pause-game?)
(defmethod pause-game? :default [_])

(defsystem manual-tick)
(defmethod manual-tick :default [_])

; fsm throws when initial-state is not part of states, so no need to assert initial-state
; initial state is nil, so associng it. make bug report at reduce-fsm?
(defn- ->init-fsm [fsm initial-state]
  (assoc (fsm initial-state nil) :state initial-state))

(defc :entity/state
  (entity/create [[k {:keys [fsm initial-state]}] eid]
    [[:e/assoc eid k (->init-fsm fsm initial-state)]
     [:e/assoc eid initial-state (entity/->v [initial-state eid])]])

  (info/text [[_ fsm]]
    (str "[YELLOW]State: " (name (:state fsm)) "[]")))

(defn state-k [entity]
  (-> entity :entity/state :state))

(defn state-obj [entity]
  (let [k (state-k entity)]
    [k (k entity)]))

(defn- send-event! [eid event params]
  (when-let [fsm (:entity/state @eid)]
    (let [old-state-k (:state fsm)
          new-fsm (fsm/fsm-event fsm event)
          new-state-k (:state new-fsm)]
      (when-not (= old-state-k new-state-k)
        (let [old-state-obj (state-obj @eid)
              new-state-obj [new-state-k (entity/->v [new-state-k eid params])]]
          [#(exit old-state-obj)
           #(enter new-state-obj)
           (when (:entity/player? @eid) #(player-enter new-state-obj))
           [:e/assoc eid :entity/state new-fsm]
           [:e/dissoc eid old-state-k]
           [:e/assoc eid new-state-k (new-state-obj 1)]])))))

(defc :tx/event
  (tx/do! [[_ eid event params]]
    (send-event! eid event params)))
