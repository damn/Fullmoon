(ns components.entity.skills
  (:require [clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx :refer [->counter stopped?]]
            [core.entity :as entity]))

(defcomponent :entity/skills
  {:data [:one-to-many-ids :properties/skill]}
  (component/create-e [[k skill-ids] eid ctx]
    (cons
     [:tx.entity/assoc eid k nil]
     (for [skill (map #(ctx/get-property ctx %) skill-ids)]
       [:tx/add-skill eid skill])))

  (component/info-text [[_ skills] _ctx]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (component/tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:tx.entity/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(extend-type core.entity.Entity
  core.entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defcomponent :tx/add-skill
  (component/do! [[_ entity {:keys [property/id] :as skill}]
               _ctx]
    (assert (not (entity/has-skill? @entity skill)))
    [[:tx.entity/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.context.action-bar/add-skill skill])]))

; unused.
(defcomponent :tx/remove-skill
  (component/do! [[_ entity {:keys [property/id] :as skill}]
                _ctx]
    (assert (entity/has-skill? @entity skill))
    [[:tx.entity/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.context.action-bar/remove-skill skill])]))
