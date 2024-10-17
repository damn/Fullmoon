(ns world.creature
  (:require [clojure.string :as str]
            [component.core :refer [defc]]
            [component.db :as db]
            [component.info :as info]
            [component.property :as property]
            [component.tx :as tx]
            [gdx.graphics :as g]
            [gdx.tiled :as tiled]
            [reduce-fsm :as fsm]
            [utils.core :refer [bind-root safe-merge tile->middle]]
            [world.core :as world]
            world.creature.states
            [world.entity :as entity]
            [world.effect :as effect]))

(defc :entity/player?
  (entity/create [_ eid]
    (bind-root #'world/player eid)
    nil))

(property/def :properties/creatures
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

(g/def-markup-color "ITEM_GOLD" [0.84 0.8 0.52])

(defc :property/pretty-name
  {:schema :string
   :let value}
  (info/text [_]
    (str "[ITEM_GOLD]"value"[]")))

(defc :body/width   {:schema pos?})
(defc :body/height  {:schema pos?})
(defc :body/flying? {:schema :boolean})

; player doesn;t need aggro-range/reaction-time
; stats armor-pierce wrong place
; assert min body size from entity

(defc :entity/body
  {:schema [:s/map [:body/width
                :body/height
                :body/flying?]]})

(defc :creature/species
  {:schema [:qualified-keyword {:namespace :species}]}
  (entity/->v [[_ species]]
    (str/capitalize (name species)))
  (info/text [[_ species]]
    (str "[LIGHT_GRAY]Creature - " species "[]")))

(defc :creature/level
  {:schema pos-int?}
  (info/text [[_ lvl]]
    (str "[GRAY]Level " lvl "[]")))

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

(defc :tx/creature
  {:let {:keys [position creature-id components]}}
  (tx/do! [_]
    (let [props (db/get creature-id)]
      [[:e/create
        position
        (->body (:entity/body props))
        (-> props
            (dissoc :entity/body)
            (assoc :entity/destroy-audiovisual :audiovisuals/creature-die)
            (safe-merge components))]])))

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

(def ^:private player-components {:entity/state {:fsm player-fsm
                                                 :initial-state :player-idle}
                                  :entity/faction :good
                                  :entity/player? true
                                  :entity/free-skill-points 3
                                  :entity/clickable {:type :clickable/player}
                                  :entity/click-distance-tiles 1.5})

(def ^:private npc-components {:entity/state {:fsm npc-fsm
                                              :initial-state :npc-sleeping}
                               :entity/faction :evil})

; player-creature needs mana & inventory
; till then hardcode :creatures/vampire
(defn- world->player-creature [start-position]
  {:position start-position
   :creature-id :creatures/vampire
   :components player-components})

(defn- world->enemy-creatures [tiled-map]
  (for [[position creature-id] (tiled/positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(def ^:private ^:dbg-flag spawn-enemies? true)

(defn spawn-all [{:keys [tiled-map start-position]}]
  (tx/do-all (for [creature (cons (world->player-creature start-position)
                                  (when spawn-enemies?
                                    (world->enemy-creatures tiled-map)))]
               [:tx/creature (update creature :position tile->middle)])))

; https://github.com/damn/core/issues/29
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
(defc :effect/spawn
  {:schema [:s/one-to-one :properties/creatures]
   :let {:keys [property/id]}}
  (effect/applicable? [_]
    (and (:entity/faction @effect/source)
         effect/target-position))

  (tx/do! [_]
    [[:tx/sound "sounds/bfxr_shield_consume.wav"]
     [:tx/creature {:position effect/target-position
                    :creature-id id ; already properties/get called through one-to-one, now called again.
                    :components {:entity/state {:fsm npc-fsm
                                                :initial-state :npc-idle}
                                 :entity/faction (:entity/faction @effect/source)}}]]))
