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

; * sounds move into action .... grep tx/sound anyway remv

; can attack own faction w. melee ..

; (component/schema :skill/effects)
; TODO how on restarts clear out core.component/attributes & defsystems  & reload _ALL_ affecet ns's defcomponents ??

; => its part of context then ?

; al data needs also to supply default value
; e.g. DRY
; convert nil cannot set it

; Bow shouldn't have player modified stuff...

; Window doesn't icnrease size on change (add components)
; anyway full size?
; also use tree? its messy ....
; => needs to be nice...

; maxrange needs to be connected w. los - max player range / creatures
; etc.
; and optional

; TODO effect/projectile
; needs direction & source & targeT?!
; its what effect-target type usable?
; player usable in just direction, NPC's use it w. target-entity to check if possible ....

; or just keep  effect - projectile fits there
; but other ones cannot use directly -
; - entity-effect -  ?

; TODO tx.ui / tx.create / tx.entity
; or with effect
; but tx. shorter

; but doesn't work with effect/hp => stats/hp
; or always effect.entity/_stat_
; autocreate

; :entity.effect/faction
; :effect.entity/faction ?
;
; :entity-effect/damage
; :hit-effect/damage
; :tx.entity/damage
; :tx.e/damage
; ?

; * convert
; * ::stat-effect
; * melee-damage
; * damage
; * stun/kill (use all events, add even 'interrupt' or destroy' ?

; => SO ITS ABOUT definint hit-effects (also use @ projectile hit-effects)
; or entity-effects???
; there are more ... also add-skill/etc.?
; see defcomponents do! ...


; TODO effect-target/position => {:effect/spawn :creatures/skeleton-warrior}
; don't pass anymore effect/source & effect/target etc.
; =. effect-target/position or direction?? {:effect/projectile :projectiles/black}
; (more complicated ...)

; TODO probably action/sound and not sound at low lvl places ... ?

; TODO can I move other skill related stuff over here? grep key usage ???

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
