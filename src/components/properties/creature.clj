(ns components.properties.creature
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

; player doesn;t need aggro-range/reaction-time
; assert min body size from core.entity
(defcomponent :body/width   {:data :pos     :optional? false})
(defcomponent :body/height  {:data :pos     :optional? false})
(defcomponent :body/flying? {:data :boolean :optional? false})

(defcomponent :entity/body
  {:data [:map [:body/width :body/height :body/flying?]]
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

; TODO cannot add components if they are optional, no :data  [:components ..]
(def ^:private entity-component-attributes
  [:property/pretty-name
   :creature/species
   :creature/level
   :entity/animation
   :entity/stats
   :entity/inventory  ; remove
   :entity/skills
   :entity/modifiers])

(defcomponent :properties/creature
  (component/create [_ _ctx]
    {:id-namespace "creatures"
     :schema [[:property/id [:qualified-keyword {:namespace :creatures}]]
              (apply vector
                     :entity/image
                     :entity/body
                     entity-component-attributes)]
     :overview {:title "Creatures"
                :columns 15
                :image/dimensions [60 60]
                :sort-by-fn #(vector (:creature/level %)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %))}}))

(defn- ->body [{:keys [body/width body/height body/flying?]}]
  {:width  width
   :height height
   :collides? true
   :z-order (if flying? :z-order/flying :z-order/ground)})

(defcomponent :tx/creature
  {:let {:keys [position creature-id components]}}
  (component/do! [_ ctx]
    (let [props (ctx/get-property ctx creature-id)]
      [[:tx/create
        position
        (->body (:entity/body props))
        (-> props
            (select-keys entity-component-attributes)
            (safe-merge components)
            (assoc :entity/destroy-audiovisual :audiovisuals/creature-die))]])))


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
     [:tx/creature {:position target-position
                    :creature-id creature-id
                    :components #:entity {:state [:state/npc :npc-idle]
                                          :faction (:entity/faction @source)}}]]))
