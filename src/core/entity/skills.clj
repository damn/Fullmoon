(ns core.entity.skills
  (:require #_[clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.ctx.time :as time]))

(defcomponent :entity/skills
  {:data [:one-to-many :properties/skills]}
  (entity/create [[k skills] eid ctx]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (component/info-text [[_ skills] _ctx]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (entity/tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (time/stopped? ctx cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(extend-type core.entity.Entity
  core.entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defcomponent :tx/add-skill
  (component/do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (not (entity/has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defcomponent :tx/remove-skill
  (component/do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (entity/has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))
