(ns entity-state.npc-idle
  (:require [api.context :refer [world-grid potential-field-follow-to-enemy]]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.world.cell :as cell]
            [effect-ctx.core :as effect-ctx]
            [entity-state.active-skill :refer [skill-usable-state]]))

; TODO here check line of sight instead @ target-entity , otherwise no target...
; also fix a schema for the effect-context so I know whats going on
(defn- ->effect-context [context entity*]
  (let [cell ((world-grid context) (entity/tile entity*))
        target (cell/nearest-entity @cell (entity/enemy-faction entity*))]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target
                         (entity/direction entity* @target))}))

(defn- npc-choose-skill [effect-ctx entity* ctx]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable
                        (skill-usable-state effect-ctx entity* %))
                     (effect-ctx/useful? effect-txs ctx)))
       first))

(defrecord NpcIdle []
  state/State
  (enter [_ entity* _ctx])
  (exit  [_ entity* _ctx])
  (tick [_ {:keys [entity/id] :as entity*} context]
    (let [effect-ctx (->effect-context context entity*)]
      (if-let [skill (npc-choose-skill effect-ctx entity* context)]
        [[:tx/event id :start-action [skill effect-ctx]]]
        [[:tx/event id :movement-direction (or (potential-field-follow-to-enemy context id)
                                               [0 0])]]))) ; nil param not accepted @ entity.state

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
