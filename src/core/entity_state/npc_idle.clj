(ns core.entity-state.npc-idle
  (:require [utils.core :refer [safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.effect.core :refer [->npc-effect-ctx]]))

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
       (filter #(and (= :usable (ctx/skill-usable-state ctx entity* %))
                     (useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/state
       entity* @(core.context/get-entity ctx uid)
       effect-ctx (->npc-effect-ctx ctx entity*)]
   (npc-choose-skill (safe-merge ctx effect-ctx) entity*))
 )

(defcomponent :npc-idle
  {:let {:keys [eid]}}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (entity/tick [_ eid ctx]
    (let [entity* @eid
          effect-ctx (->npc-effect-ctx ctx entity*)]
      (if-let [skill (npc-choose-skill (safe-merge ctx effect-ctx) entity*)]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (ctx/potential-field-follow-to-enemy ctx eid)]]))))
