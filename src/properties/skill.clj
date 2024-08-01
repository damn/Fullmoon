(ns properties.skill
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number]]
            [core.component :as component]
            [core.data :as data]
            [api.context :as ctx]
            effect.all))

(component/def :skill/action-time data/pos-attr)
(component/def :skill/cooldown data/nat-int-attr)
(component/def :skill/cost data/nat-int-attr)
(component/def :skill/effect (data/components-attribute :effect))
(component/def :skill/start-action-sound data/sound)
(component/def :skill/action-time-modifier-key (data/enum :stats/cast-speed :stats/attack-speed))

(def ^:private skill-cost-color "[CYAN]")
(def ^:private action-time-color "[GOLD]")
(def ^:private cooldown-color "[SKY]")
(def ^:private effect-color "[CHARTREUSE]")

(def definition
  {:property.type/skill
   {:of-type? :skill/effect
    :edn-file-sort-order 0
    :title "Skill"
    :overview {:title "Skill"
               :columns 16
               :image/dimensions [70 70]}
    :schema (data/map-attribute-schema
             [:property/id [:qualified-keyword {:namespace :skills}]]
             [:property/image
              :skill/action-time
              :skill/cooldown
              :skill/cost
              :skill/effect
              :skill/start-action-sound
              :skill/action-time-modifier-key])
    :->text (fn [ctx {:keys [property/id
                             skill/action-time
                             skill/cooldown
                             skill/cost
                             skill/effect
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
               (str effect-color (ctx/effect-text ctx effect) "[]")])}})
