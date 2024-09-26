(ns core.skill
  (:require [clojure.ctx :refer :all]))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @player-entity))
(defn ->skill-window [context]
  (->window {:title "Skills"
             :id :skill-window
             :visible? false
             :cell-defaults {:pad 10}
             :rows [(for [id [:skills/projectile
                              :skills/meditation
                              :skills/spawn
                              :skills/melee-attack]
                          :let [; get-property in callbacks if they get changed, this is part of context permanently
                                button (->image-button ; TODO reuse actionbar button scale?
                                                       (:entity/image (build-property context id)) ; TODO here anyway taken
                                                       ; => should probably build this window @ game start
                                                       (fn [ctx]
                                                         (effect! ctx (player-clicked-skillmenu ctx (build-property ctx id)))))]]
                      (do
                       (add-tooltip! button #(->info-text (build-property % id) %)) ; TODO no player modifiers applied (see actionbar)
                       button))]
             :pack? true}))

(defcomponent :skill/action-time {:data :pos}
  (info-text [[_ v] _ctx]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defcomponent :skill/cooldown {:data :nat-int}
  (info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defcomponent :skill/cost {:data :nat-int}
  (info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defcomponent :skill/effects
  {:data [:components-ns :effect]})

(defcomponent :skill/start-action-sound {:data :sound})

(defcomponent :skill/action-time-modifier-key
  {:data [:enum [:stats/cast-speed :stats/attack-speed]]}
  (info-text [[_ v] _ctx]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defcomponent :entity/skills
  {:data [:one-to-many :properties/skills]}
  (create [[k skills] eid ctx]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (info-text [[_ skills] _ctx]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defcomponent :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defcomponent :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))
