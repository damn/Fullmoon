(ns core.entity.state.npc-idle
  (:require [core.utils.core :refer [safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.effect.core :refer [->npc-effect-ctx]]
            [core.ctx.potential-fields :as potential-fields]
            [core.entity.state.active-skill :refer [skill-usable-state]]))

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
       (filter #(and (= :usable (skill-usable-state ctx entity* %))
                     (useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/state
       entity* @(core.ctx.ecs/get-entity ctx uid)
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
        [[:tx/event eid :movement-direction (potential-fields/follow-to-enemy ctx eid)]]))))
