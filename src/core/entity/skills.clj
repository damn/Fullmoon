(ns core.entity.skills
  (:require #_[clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.time :as time]
            [core.tx :as tx]))

(defcomponent :entity/skills
  {:data [:one-to-many :properties/skills]}
  (entity/create [[k skills] eid ctx]
    (cons [:tx/assoc eid k nil]
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
      [:tx/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(extend-type core.entity.Entity
  core.entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defcomponent :tx/add-skill
  (tx/do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (not (entity/has-skill? @entity skill)))
    [[:tx/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defcomponent :tx/remove-skill
  (tx/do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (entity/has-skill? @entity skill))
    [[:tx/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))
