(ns entity-state.npc-idle
  (:require [utils.core :refer [safe-merge]]
            [api.context :as ctx :refer [world-grid potential-field-follow-to-enemy]]
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
    (ctx/map->Context
     {:effect/source (:entity/id entity*)
      :effect/target target
      :effect/direction (when target (entity/direction entity* @target))})))

; TODO
; split it into 3 parts
; applicable
; useful
; usable?
(defn- useful? [ctx effects]
  ;(println "Check useful? for effects: " (map first effects))
  (let [applicable-effects (filter #(effect/applicable? % ctx) effects)
        ;_ (println "applicable-effects: " (map first applicable-effects))
        useful-effect (some #(effect/useful? % ctx) applicable-effects)]
    ;(println "Useful: " useful-effect)
    useful-effect))

(defn- npc-choose-skill [ctx entity*]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill-usable-state ctx entity* %))
                     (useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/current-context
       entity* @(api.context/get-entity ctx uid)
       effect-ctx (->effect-context ctx entity*)]
   (npc-choose-skill (safe-merge ctx effect-ctx) entity*))
 )

(defrecord NpcIdle []
  state/State
  (enter [_ entity* _ctx])
  (exit  [_ entity* _ctx])
  (tick [_ entity context]
    (let [entity* @entity
          effect-ctx (->effect-context context entity*)]
      (if-let [skill (npc-choose-skill (safe-merge context effect-ctx) entity*)]
        [[:tx/event entity :start-action [skill effect-ctx]]]
        [[:tx/event entity :movement-direction (or (potential-field-follow-to-enemy context entity)
                                                   [0 0])]]))) ; nil param not accepted @ entity.state

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
