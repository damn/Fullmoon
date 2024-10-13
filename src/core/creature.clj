(ns core.creature
  (:require [clojure.string :as str]
            [core.component :refer [defc do!] :as component]
            [core.property :as property]
            [core.db :as db]
            [utils.core :refer [bind-root safe-merge]]
            world.creature.states
            [world.entity :as entity]
            [world.player :refer [world-player]]))

(defc :entity/player?
  (entity/create [_ eid]
    (bind-root #'world-player eid)
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

(defc :body/width   {:data :pos})
(defc :body/height  {:data :pos})
(defc :body/flying? {:data :boolean})

; player doesn;t need aggro-range/reaction-time
; stats armor-pierce wrong place
; assert min body size from core.entity

(defc :entity/body
  {:data [:map [:body/width
                :body/height
                :body/flying?]]})

(defc :creature/species
  {:data [:qualified-keyword {:namespace :species}]}
  (component/create [[_ species]]
    (str/capitalize (name species)))
  (component/info [[_ species]]
    (str "[LIGHT_GRAY]Creature - " species "[]")))

(defc :creature/level
  {:data :pos-int}
  (component/info [[_ lvl]]
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

(defn- set-fsm [components]
  (if (:entity/state components)
    (update components :entity/state
            (fn [[player-or-npc initial-state]]
              {:initial-state initial-state
               :fsm (case player-or-npc
                      :state/player world.creature.states/player-fsm
                      :state/npc    world.creature.states/npc-fsm)}))
    components))

(defc :tx/creature
  {:let {:keys [position creature-id components]}}
  (do! [_]
    (let [props (db/get creature-id)]
      [[:e/create
        position
        (->body (:entity/body props))
        (-> props
            (dissoc :entity/body)
            (assoc :entity/destroy-audiovisual :audiovisuals/creature-die)
            (safe-merge (set-fsm components)))]])))
