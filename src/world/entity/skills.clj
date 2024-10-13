(ns world.entity.skills
  (:require [core.component :refer [defc defsystem] :as component]
            [core.effect :refer [do!]]
            [core.property :as property]
            [utils.core :refer [readable-number]]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [world.player :refer [world-player]]
            [world.time :refer [stopped?]]))

(property/def :properties/skills
  {:schema [:entity/image
            :property/pretty-name
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/start-action-sound
            :skill/effects
            [:skill/cooldown {:optional true}]
            [:skill/cost {:optional true}]]
   :overview {:title "Skills"
              :columns 16
              :image/scale 2}})

(defc :skill/action-time-modifier-key
  {:data [:enum :stats/cast-speed :stats/attack-speed]}
  (component/info [[_ v]]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defc :skill/action-time {:data :pos}
  (component/info [[_ v]]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defc :skill/start-action-sound {:data :sound})

(defc :skill/effects
  {:data [:components-ns :effect]})

(defc :skill/cooldown {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defc :skill/cost {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defc :entity/skills
  {:data [:one-to-many :properties/skills]}
  (entity/create [[k skills] eid]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (component/info [[_ skills]]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (entity/tick [[k skills] eid]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defc :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defc :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))

(defsystem clicked-skillmenu-skill [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])

(defn- player-clicked-skillmenu [skill]
  (clicked-skillmenu-skill (entity-state/state-obj @world-player) skill))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @world-player))
#_(defn ->skill-window []
    (ui/window {:title "Skills"
                :id :skill-window
                :visible? false
                :cell-defaults {:pad 10}
                :rows [(for [id [:skills/projectile
                                 :skills/meditation
                                 :skills/spawn
                                 :skills/melee-attack]
                             :let [; get-property in callbacks if they get changed, this is part of context permanently
                                   button (ui/image-button ; TODO reuse actionbar button scale?
                                                           (:entity/image (db/get id)) ; TODO here anyway taken
                                                           ; => should probably build this window @ game start
                                                           (fn []
                                                             (effect! (player-clicked-skillmenu (db/get id)))))]]
                         (do
                          (ui/add-tooltip! button #(component/info-text (db/get id))) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))
