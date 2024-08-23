(ns components.entity.skills
  (:require [clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.context :refer [get-property ->counter stopped?]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.data :as data]))

; FIXME starting skills do not trigger :tx.context.action-bar/add-skill
; https://trello.com/c/R6GSIDO1/363

; required by npc state, also mana!, also movement (no not needed, doesnt do anything then)
(defcomponent :entity/skills (data/one-to-many-ids :properties/skill)
  (component/create [[_ skill-ids] ctx]
    (zipmap skill-ids (map #(get-property ctx %) skill-ids)))

  (component/info-text [[_ skills] _ctx]
    (when (seq skills)
      (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (entity/tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:tx.entity/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(extend-type core.entity.Entity
  entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defcomponent :tx/add-skill {}
  (effect/do! [[_ entity {:keys [property/id] :as skill}]
               _ctx]
    (assert (not (entity/has-skill? @entity skill)))
    [[:tx.entity/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.context.action-bar/add-skill skill])]))

; unused ?
(defcomponent :tx/remove-skill {}
  (effect/do! [[_ entity {:keys [property/id] :as skill}]
                _ctx]
    (assert (entity/has-skill? @entity skill))
    [[:tx.entity/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.context.action-bar/remove-skill skill])]))
