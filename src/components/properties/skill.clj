(ns components.properties.skill
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]))

(def ^:private skill-cost-color "[CYAN]")
(def ^:private action-time-color "[GOLD]")
(def ^:private cooldown-color "[SKY]")
(def ^:private effect-color "[CHARTREUSE]")

(defcomponent :skill/action-time              {:data :pos})
(defcomponent :skill/cooldown                 {:data :nat-int})
(defcomponent :skill/cost                     {:data :nat-int})
(defcomponent :skill/effects                  {:data [:components-ns :effect]})
(defcomponent :skill/start-action-sound       {:data :sound})
(defcomponent :skill/action-time-modifier-key {:data [:enum :stats/cast-speed :stats/attack-speed]})

(defcomponent :properties/skill
  (component/create [_ _ctx]
    {:id-namespace "skills"
     :schema [[:property/id [:qualified-keyword {:namespace :skills}]]
              [:property/image
               :skill/action-time
               :skill/cooldown
               :skill/cost
               :skill/effects
               :skill/start-action-sound
               :skill/action-time-modifier-key]]
     :edn-file-sort-order 0
     :overview {:title "Skills"
                :columns 16
                :image/dimensions [70 70]}
     :->text (fn [ctx {:keys [property/id
                              skill/action-time
                              skill/cooldown
                              skill/cost
                              skill/effects
                              skill/action-time-modifier-key]}]
               [(str/capitalize (name id))
                (str skill-cost-color "Cost: " cost "[]")
                (str action-time-color
                     (case action-time-modifier-key
                       :stats/cast-speed "Casting-Time"
                       :stats/attack-speed "Attack-Time")
                     ": "
                     (readable-number action-time) " seconds" "[]")
                (str cooldown-color "Cooldown: " (readable-number cooldown) "[]")
                ; don't used player-entity* as it may be nil when just created, could use the current property creature @ editor
                (str effect-color
                     (ctx/effect-text (assoc ctx :effect/source (ctx/player-entity ctx))
                                      effects)
                     "[]")])}))
