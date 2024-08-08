(ns properties.skill
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :as ctx]
            [api.properties :as properties]
            [effect-ctx.core :as effect-ctx]))

(def ^:private skill-cost-color "[CYAN]")
(def ^:private action-time-color "[GOLD]")
(def ^:private cooldown-color "[SKY]")
(def ^:private effect-color "[CHARTREUSE]")

(defcomponent :properties/skill {}
  (properties/create [_]
    (defcomponent :skill/action-time data/pos-attr)
    (defcomponent :skill/cooldown data/nat-int-attr)
    (defcomponent :skill/cost data/nat-int-attr)
    (defcomponent :skill/effect (data/components-attribute :effect))
    (defcomponent :skill/start-action-sound data/sound)
    (defcomponent :skill/action-time-modifier-key (data/enum :stats/cast-speed :stats/attack-speed))
    {:id-namespace "skills"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :skills}]]
              [:property/image
               :skill/action-time
               :skill/cooldown
               :skill/cost
               :skill/effect
               :skill/start-action-sound
               :skill/action-time-modifier-key])
     :edn-file-sort-order 0
     :overview {:title "Skill"
                :columns 16
                :image/dimensions [70 70]}
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
                ; don't used player-entity* as it may be nil when just created
                (str effect-color (effect-ctx/text {:effect/source (:context.game/player-entity ctx)}
                                                   effect) "[]")])}))
