(ns components.entity-state.npc-idle
  (:require [utils.core :refer [safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx :refer [world-grid potential-field-follow-to-enemy]]
            [core.entity :as entity]
            [core.world.cell :as cell]))

(defn- nearest-enemy [ctx entity*]
  (cell/nearest-entity @((world-grid ctx) (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn- ->effect-ctx [ctx entity*]
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
  (let [applicable-effects (filter #(component/applicable? % ctx) effects)
        ;_ (println "applicable-effects: " (map first applicable-effects))
        useful-effect (some #(component/useful? % ctx) applicable-effects)]
    ;(println "Useful: " useful-effect)
    useful-effect))

(defn- npc-choose-skill [ctx entity*]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (ctx/skill-usable-state ctx entity* %))
                     (useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/state
       entity* @(core.context/get-entity ctx uid)
       effect-ctx (->effect-context ctx entity*)]
   (npc-choose-skill (safe-merge ctx effect-ctx) entity*))
 )

(defcomponent :npc-idle
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (entity/tick [_ eid ctx]
    (let [entity* @eid
          effect-ctx (->effect-ctx ctx entity*)]
      (if-let [skill (npc-choose-skill (safe-merge ctx effect-ctx) entity*)]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (potential-field-follow-to-enemy ctx eid)]]))))
