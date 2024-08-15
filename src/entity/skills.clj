(ns entity.skills
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [api.context :refer [get-property ->counter stopped?]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [core.data :as data]))

; FIXME starting skills do not trigger :tx.context.action-bar/add-skill
; https://trello.com/c/R6GSIDO1/363

; required by npc state, also mana!, also movement (no not needed, doesnt do anything then)
(defcomponent :entity/skills (data/one-to-many-ids :properties/skill)
  (entity/create-component [[_ skill-ids] _components ctx]
    (zipmap skill-ids (map #(get-property ctx %) skill-ids)))

  (entity/info-text [[_ skills] _ctx]
    (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]"))

  (entity/tick [[k skills] entity* ctx]
    (for [{:keys [property/id skill/cooling-down?]} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:tx.entity/assoc-in (:entity/id entity*) [k id :skill/cooling-down?] false])))

(extend-type api.entity.Entity
  entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defmethod transact! :tx/add-skill [[_ entity {:keys [property/id] :as skill}]
                                    _ctx]
  (assert (not (entity/has-skill? @entity skill)))
  [[:tx.entity/assoc-in entity [:entity/skills id] skill]
   (when (:entity/player? @entity)
     [:tx.context.action-bar/add-skill skill])])

; unused ?
(defmethod transact! :tx/remove-skill [[_ entity {:keys [property/id] :as skill}]
                                       _ctx]
  (assert (entity/has-skill? @entity skill))
  [[:tx.entity/dissoc-in entity [:entity/skills id]]
   (when (:entity/player? @entity)
     [:tx.context.action-bar/remove-skill skill])])
