(ns components.properties.creature
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

; TODO assert min body size from core.entity
; TODO make px
(defcomponent :property/bounds
  {:data :some
   :optional? false})

(defcomponent :creature/species
  {:data [:qualified-keyword {:namespace :species}]
   :optional? false}
  (component/create [[_ species] _ctx]
    (str/capitalize (name species)))
  (component/info-text [[_ species] _ctx]
    (str "[LIGHT_GRAY]Species: " species "[]")))

(defcomponent :creature/level
  {:data :pos-int
   :optional? false}
  (component/info-text [[_ lvl] _ctx]
    (str "[GRAY]Level " lvl "[]")))

(defcomponent :entity/flying?
  {:data :boolean
   :optional? true})

(defcomponent :entity/reaction-time
  {:data :pos
   :optional? false})

; TODO cannot add components if they are optional, no :data  [:components ..]

(def ^:private entity-component-attributes
  [:property/pretty-name
   :creature/species
   :creature/level
   :entity/animation
   :entity/reaction-time ; in frames 0.016x
   :entity/stats
   :entity/inventory  ; remove
   :entity/skills])

(defcomponent :properties/creature
  (component/create [_ _ctx]
    {:id-namespace "creatures"
     :schema [[:property/id [:qualified-keyword {:namespace :creatures}]]
              (apply vector
                     ; property
                     :property/image
                     ; body
                     :property/bounds
                     :entity/flying?
                     entity-component-attributes)]
     :edn-file-sort-order 1
     :overview {:title "Creatures"
                :columns 15
                :image/dimensions [60 60]
                :sort-by-fn #(vector (:creature/level %)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %))}}))

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

; if controller = :controller/player
; -> add those fields
; :player? true ; -> api -> 'entity/player?' fn
; :free-skill-points 3
; :clickable {:type :clickable/player}
; :click-distance-tiles 1.5

; otherwise

(defn- ->body [position props]
  {:position position
   :width  (:width  (:property/bounds props))
   :height (:height (:property/bounds props))
   :collides? true
   :z-order (if (:entity/flying? props)
              :z-order/flying
              :z-order/ground)})

(defcomponent :tx.entity/creature
  {:let {:keys [position creature-id components]}}
  (component/do! [_ ctx]
    (let [props (ctx/get-property ctx creature-id)]
      [[:tx/create
        (->body position props)
        (-> props
            (select-keys entity-component-attributes)
            (safe-merge components)
            (assoc :destroy-audiovisual :audiovisuals/creature-die))]])))


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
     [:tx.entity/creature {:position target-position
                           :creature-id creature-id
                           :components #:entity {:state [:state/npc :npc-idle]
                                                 :faction (:entity/faction @source)}}]]))
