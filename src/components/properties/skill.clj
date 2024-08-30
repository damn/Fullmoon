(ns components.properties.skill
  (:require [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent] :as component]))

(defcomponent :skill/action-time
  {:data :pos
   :optional? false}
  (component/info-text [[_ v] _ctx]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defcomponent :skill/cooldown {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defcomponent :skill/cost {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defcomponent :skill/effects
  {:data [:components-ns :effect]
   :optional? false})

(defcomponent :skill/start-action-sound
  {:data :sound
   :optional? false})

(defcomponent :skill/action-time-modifier-key
  {:data [:enum [:stats/cast-speed :stats/attack-speed]]
   :optional? false}
  (component/info-text [[_ v] _ctx]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defcomponent :properties/skill
  (component/create [_ _ctx]
    {:id-namespace "skills"
     :schema [[:property/id [:qualified-keyword {:namespace :skills}]]
              [:property/pretty-name
               :entity/image
               :skill/action-time
               :skill/cooldown
               :skill/cost
               :skill/effects
               :skill/start-action-sound
               :skill/action-time-modifier-key]]
     :overview {:title "Skills"
                :columns 16
                :image/scale 2}}))
