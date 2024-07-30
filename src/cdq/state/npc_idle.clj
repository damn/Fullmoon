(ns cdq.state.npc-idle
  (:require [cdq.api.context :refer [effect-useful? world-grid potential-field-follow-to-enemy skill-usable-state]]
            [cdq.api.entity :as entity]
            [cdq.api.state :as state]
            [cdq.api.world.cell :as cell]))

(defn- effect-context [context entity*]
  (let [cell ((world-grid context) (entity/tile entity*))
        target (cell/nearest-entity @cell (entity/enemy-faction entity*))]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target
                         (entity/direction entity* @target))}))

(defn- npc-choose-skill [effect-context entity*]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable
                        (skill-usable-state effect-context entity* %))
                     (effect-useful? effect-context (:skill/effect %))))
       first))

(defrecord NpcIdle []
  state/State
  (enter [_ entity* _ctx])
  (exit  [_ entity* _ctx])
  (tick [_ {:keys [entity/id] :as entity*} context]
    (let [effect-context (effect-context context entity*)]
      (if-let [skill (npc-choose-skill (merge context effect-context) entity*)]
        [[:tx/event id :start-action [skill effect-context]]]
        [[:tx/event id :movement-direction (or (potential-field-follow-to-enemy context id)
                                               [0 0])]]))) ; nil param not accepted @ entity.state

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
