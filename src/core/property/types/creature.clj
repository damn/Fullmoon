(ns core.property.types.creature
  (:require [clojure.string :as str]
            [utils.core :refer [safe-merge]]
            [core.component :as component :refer [defcomponent]]
            [core.ctx.property :as property]
            [core.effect :as effect]
            [core.tx :as tx]))

; player doesn;t need aggro-range/reaction-time
; stats armor-pierce wrong place
; assert min body size from core.entity

(property/def-attributes
  :body/width   :pos
  :body/height  :pos
  :body/flying? :boolean)

(defcomponent :entity/body
  {:data [:map [:body/width
                :body/height
                :body/flying?]]})

(defcomponent :creature/species
  {:data [:qualified-keyword {:namespace :species}]}
  (component/create [[_ species] _ctx]
    (str/capitalize (name species)))
  (component/info-text [[_ species] _ctx]
    (str "[LIGHT_GRAY]Creature - " species "[]")))

(defcomponent :creature/level
  {:data :pos-int}
  (component/info-text [[_ lvl] _ctx]
    (str "[GRAY]Level " lvl "[]")))

(property/def-type :properties/creatures
  {:schema [:entity/body
            :property/pretty-name
            :creature/species
            :creature/level
            :entity/animation
            :entity/stats
            :entity/skills
            [:entity/modifiers {:optional true}]
            [:entity/inventory {:optional true}]]
   :overview {:title "Creatures"
              :columns 15
              :image/scale 1.5
              :sort-by-fn #(vector (:creature/level %)
                             (name (:creature/species %))
                             (name (:property/id %)))
              :extra-info-text #(str (:creature/level %))}})

; # :z-order/flying has no effect for now
; * entities with :z-order/flying are not flying over water,etc. (movement/air)
; because using potential-field for z-order/ground
; -> would have to add one more potential-field for each faction for z-order/flying
; * they would also (maybe) need a separate occupied-cells if they don't collide with other
; * they could also go over ground units and not collide with them
; ( a test showed then flying OVER player entity )
; -> so no flying units for now
(defn- ->body [{:keys [body/width body/height #_body/flying?]}]
  {:width  width
   :height height
   :collides? true
   :z-order :z-order/ground #_(if flying? :z-order/flying :z-order/ground)})

(defcomponent :tx/creature
  {:let {:keys [position creature-id components]}}
  (tx/do! [_ ctx]
    (let [props (property/build ctx creature-id)]
      [[:e/create
        position
        (->body (:entity/body props))
        (-> props
            (dissoc :entity/body)
            (assoc :entity/destroy-audiovisual :audiovisuals/creature-die)
            (safe-merge components))]])))

; TODO https://github.com/damn/core/issues/29
; spawning on player both without error ?! => not valid position checked
; also what if someone moves on the target posi ? find nearby valid cell ?
; BLOCKING PLAYER MOVEMENT ! (summons no-clip with player ?)
; check not blocked position // line of sight. (part of target-position make)
; limit max. spawns
; animation/sound
; proper icon (grayscaled ?)
; keep in player movement range priority ( follow player if too far, otherwise going for enemies)
; => so they follow you around
; not try-spawn, but check valid-params & then spawn !
; new UI -> show creature body & then place
; >> but what if it is blocked the area during action-time ?? <<
(defcomponent :effect/spawn
  {:data [:one-to-one :properties/creatures]
   :let {:keys [property/id]}}
  (effect/applicable? [_ {:keys [effect/source effect/target-position]}]
    (and (:entity/faction @source)
         target-position))

  (tx/do! [_ {:keys [effect/source effect/target-position]}]
    [[:tx/sound "sounds/bfxr_shield_consume.wav"]
     [:tx/creature {:position target-position
                    :creature-id id ; already properties/get called through one-to-one, now called again.
                    :components {:entity/state [:state/npc :npc-idle]
                                 :entity/faction (:entity/faction @source)}}]]))
