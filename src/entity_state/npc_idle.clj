(ns entity-state.npc-idle
  (:require [api.context :as ctx :refer [world-grid potential-field-follow-to-enemy]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.world.cell :as cell]
            [entity-state.active-skill :refer [skill-usable-state]]))

(defn- nearest-enemy [ctx entity*]
  (cell/nearest-entity @((world-grid ctx) (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn- ->effect-context [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (ctx/line-of-sight? ctx entity* @target))
                 target)]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target (entity/direction entity* @target))}))

(defn- useful? [effect-ctx effects ctx]
  ;(println "Check useful? for effects: " (map first effects))
  (let [applicable-effects (filter #(effect/applicable? % effect-ctx) effects)
        ;_ (println "applicable-effects: " (map first applicable-effects))
        useful-effect (some #(effect/useful? % effect-ctx ctx) applicable-effects)]
    ;(println "Useful: " useful-effect)
    useful-effect))

(defn- npc-choose-skill [effect-ctx entity* ctx]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill-usable-state effect-ctx entity* % ctx))
                     (useful? effect-ctx (:skill/effects %) ctx)))
       first))

(comment
 (let [uid 76
       ctx @app/current-context
       entity* @(api.context/get-entity ctx uid)
       effect-ctx (->effect-context ctx entity*)]
   (npc-choose-skill effect-ctx entity* ctx))
 )

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
