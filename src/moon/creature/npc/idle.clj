(ns moon.creature.npc.idle
  (:require [component.core :refer [defc]]
            [moon.skill :as skill]
            [world.core :as world]
            [world.entity :as entity]
            [world.entity.follow-ai :as follow-ai]
            [world.effect :as effect]))

(comment
 (let [eid (entity/get-entity 76)
       effect-ctx (effect/npc-ctx eid)]
   (npc-choose-skill effect-ctx @eid))
 )

(defn- npc-choose-skill [entity]
  (->> entity
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill/usable-state entity %))
                     (effect/effect-useful? (:skill/effects %))))
       first))

(defc :npc-idle
  {:let {:keys [eid]}}
  (entity/->v [[_ eid]]
    {:eid eid})

  (entity/tick [_ eid]
    (let [effect-ctx (effect/npc-ctx eid)]
      (if-let [skill (effect/with-ctx effect-ctx
                       (npc-choose-skill @eid))]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (follow-ai/direction-vector eid)]]))))
