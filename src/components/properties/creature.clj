(ns components.properties.creature
  (:require [clojure.string :as str]
            [reduce-fsm :as fsm]
            [utils.core :refer [readable-number]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]))

              ; TODO assert min body size from core.entity
              ; TODO make px
(defcomponent :property/bounds      {:data :some})
(defcomponent :creature/species     {:data [:qualified-keyword {:namespace :species}]})
(defcomponent :creature/level       {:data :pos-int})
(defcomponent :entity/flying?       {:data :boolean})
(defcomponent :entity/reaction-time {:data :pos})
(defcomponent :creature/entity {:data [:components ; TODO no required/optional settings ! just cannot remove & already there !
                                       :entity/animation
                                       :entity/flying? ; remove
                                       :entity/reaction-time ; in frames 0.016x
                                       :entity/faction ; remove
                                       :entity/stats
                                       :entity/inventory  ; remove
                                       :entity/skills]})

(defcomponent :entity.creature/name
  (component/info-text [[_ name] _ctx]
    name))

(defcomponent :entity.creature/species
  (component/info-text [[_ species] _ctx]
    (str "[LIGHT_GRAY]Species: " species "[]")))

(defcomponent :properties/creature
  (component/create [_ _ctx]
    {:id-namespace "creatures"
     :schema [[:property/id [:qualified-keyword {:namespace :creatures}]]
              [:property/image
               :property/bounds
               :creature/species
               :creature/level
               :creature/entity]]
     :edn-file-sort-order 1
     :overview {:title "Creatures"
                :columns 15
                :image/dimensions [60 60]
                :sort-by-fn #(vector (:creature/level %)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %)
                                       #_(case (:entity/faction (:creature/entity %))
                                         :good "g"
                                         :evil "e"))}
     :->text (fn [_ctx
                  {:keys [property/id
                          creature/species
                          creature/level
                          creature/entity]}]
               #_[(str/capitalize (name id)) ; == pretty name
                (str "Species: " (str/capitalize (name species)))
                (when level (str "Level: " level))
                (binding [*print-level* nil]
                  (with-out-str
                   (clojure.pprint/pprint
                    (select-keys entity
                                 [;:entity/animation
                                  :entity/faction
                                  :entity/flying?
                                  :entity/reaction-time
                                  :entity/inventory
                                  :entity/skills
                                  :entity/stats]))))])}))

(comment
 ; graphviz required in path
 (fsm/show-fsm player-fsm)

 )

(fsm/defsm-inc ^:private player-fsm
  [[:player-idle
    :kill -> :player-dead
    :stun -> :stunned
    :start-action -> :active-skill
    :pickup-item -> :player-item-on-cursor
    :movement-input -> :player-moving]
   [:player-moving
    :kill -> :player-dead
    :stun -> :stunned
    :no-movement-input -> :player-idle]
   [:active-skill
    :kill -> :player-dead
    :stun -> :stunned
    :action-done -> :player-idle]
   [:stunned
    :kill -> :player-dead
    :effect-wears-off -> :player-idle]
   [:player-item-on-cursor
    :kill -> :player-dead
    :stun -> :stunned
    :drop-item -> :player-idle
    :dropped-item -> :player-idle]
   [:player-dead]])

(defn ->player-state [initial-state]
  {:initial-state initial-state
   :fsm player-fsm})

(fsm/defsm-inc ^:private npc-fsm
  [[:npc-sleeping
    :kill -> :npc-dead
    :stun -> :stunned
    :alert -> :npc-idle]
   [:npc-idle
    :kill -> :npc-dead
    :stun -> :stunned
    :start-action -> :active-skill
    :movement-direction -> :npc-moving]
   [:npc-moving
    :kill -> :npc-dead
    :stun -> :stunned
    :timer-finished -> :npc-idle]
   [:active-skill
    :kill -> :npc-dead
    :stun -> :stunned
    :action-done -> :npc-idle]
   [:stunned
    :kill -> :npc-dead
    :effect-wears-off -> :npc-idle]
   [:npc-dead]])

(defn ->npc-state [initial-state]
  {:initial-state initial-state
   :fsm npc-fsm})

(defcomponent :effect.entity/stun
  {:data :pos
   :let duration}
  (component/info-text [_ _effect-ctx]
    (str "Stuns for " (readable-number duration) " seconds"))

  (component/applicable? [_ {:keys [effect/target]}]
    (and target
         (:entity/state @target)))

  (component/do! [_ {:keys [effect/target]}]
    [[:tx/event target :stun duration]]))

(defcomponent :effect.entity/kill
  {:data :some}
  (component/info-text [_ _effect-ctx]
    "Kills target")

  (component/applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (:entity/state @target)))

  (component/do! [_ {:keys [effect/target]}]
    [[:tx/event target :kill]]))

; TODO @ properties.creature set optional/obligatory .... what is needed ???
; body
; skills
; mana
; stats (cast,attack-speed -> move to skills?)
; movement (if should move w. movement-vector ?!, otherwise still in 'moving' state ... )

; npc:
; reaction-time
; faction

; player:
; click-distance-tiles
; free-skill-points
; inventory
; item-on-cursor (added by itself)


;;;; add 'controller'
; :type controller/npc or controller/player
;;; dissoc here and assign components ....
; only npcs need reaction time ....

; TODO move to entity/state component, don'tneed to know about that here .... >
; but what about controller component stuff ?
; or entity/controller creates all of this ?
(defn- set-state [[player-or-npc initial-state]]
  ((case player-or-npc
     :state/player ->player-state
     :state/npc ->npc-state)
   initial-state))

; if controller = :controller/player
; -> add those fields
; :player? true ; -> api -> 'entity/player?' fn
; :free-skill-points 3
; :clickable {:type :clickable/player}
; :click-distance-tiles 1.5

; otherwise

(defcomponent :tx.entity/creature
  (component/do! [[_ creature-id components] ctx]
    (assert (:entity/state components))
    (let [props (ctx/get-property ctx creature-id)
          creature-components (:creature/entity props)]
      [[:tx/create
        {:position (:entity/position components)
         :width  (:width  (:property/bounds props))
         :height (:height (:property/bounds props))
         :collides? true
         :z-order (if (:entity/flying? creature-components)
                    :z-order/flying
                    :z-order/ground)}
        (-> creature-components
            (dissoc :entity/flying?)
            (merge (dissoc components :entity/position))
            (update :entity/state set-state)  ; do @ entity/state itself
            (assoc :entity.creature/name    (str/capitalize (name (:property/id props)))
                   :entity.creature/species (str/capitalize (name (:creature/species props)))
                   :entity/destroy-audiovisual :audiovisuals/creature-die))]])))

(comment

 (set! *print-level* nil)
 (clojure.pprint/pprint
  (component/do! [:tx.entity/creature :creatures/vampire {:entity/position [1 2]
                                                       :entity/state [:state/npc :npc-sleeping]}]
              (reify core.context/PropertyStore
                (get-property [_ id]
                  {:property/width 5
                   :property/height 5
                   :creature/entity {:entity/flying? true}}))))

 )


; TODO spawning on player both without error ?! => not valid position checked
; also what if someone moves on the target posi ? find nearby valid cell ?

; BLOCKING PLAYER MOVEMENT ! (summons no-clip with player ?)
; check not blocked position // line of sight.
; limit max. spawns
; animation/sound
; proper icon (grayscaled ?)
; keep in player movement range priority ( follow player if too far, otherwise going for enemies)
; => so they follow you around

; not try-spawn, but check valid-params & then spawn !

; new UI -> show creature body & then place
; >> but what if it is blocked the area during action-time ?? <<

; Also: to make a complete game takes so many creatures, items, skills, balance, ui changes, testing
; is it even possible ?

(comment
 ; keys: :faction(:source)/:target-position/:creature-id
 )

; => one to one attr!?
(defcomponent :effect/spawn
  {:data [:qualified-keyword {:namespace :creatures}]
   :let creature-id}
  (component/info-text [_ _effect-ctx]
    (str "Spawns a " (name creature-id)))

  (component/applicable? [_ {:keys [effect/source effect/target-position]}]
    ; TODO line of sight ? / not blocked tile..
    ; (part of target-position make)
    (and (:entity/faction @source)
         target-position))

  (component/do! [_ {:keys [effect/source effect/target-position]}]
    [[:tx/sound "sounds/bfxr_shield_consume.wav"]
     [:tx.entity/creature
      creature-id
      #:entity {:position target-position
                :state [:state/npc :npc-idle]
                :faction (:entity/faction @source)}]]))
