(ns world.creature
  (:require [clojure.gdx.graphics :as g]
            [clojure.string :as str]
            [core.component :refer [defc]]
            [core.db :as db]
            [core.info :as info]
            [core.tx :as tx]
            [utils.core :refer [bind-root safe-merge]]
            [world.core :as world]
            [world.creature.fsms :as fsms]
            world.creature.states
            [world.entity :as entity]))

(defc :entity/player?
  (entity/create [_ eid]
    (bind-root #'world/player eid)
    nil))

(db/def-property :properties/creatures
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
  {:db/schema :string
   :let value}
  (info/text [_]
    (str "[ITEM_GOLD]"value"[]")))

(defc :body/width   {:db/schema :pos})
(defc :body/height  {:db/schema :pos})
(defc :body/flying? {:db/schema :boolean})

; player doesn;t need aggro-range/reaction-time
; stats armor-pierce wrong place
; assert min body size from core.entity

(defc :entity/body
  {:db/schema [:map [:body/width
                     :body/height
                     :body/flying?]]})

(defc :creature/species
  {:db/schema [:qualified-keyword {:namespace :species}]}
  (entity/->v [[_ species]]
    (str/capitalize (name species)))
  (info/text [[_ species]]
    (str "[LIGHT_GRAY]Creature - " species "[]")))

(defc :creature/level
  {:db/schema :pos-int}
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

(defn- set-fsm [components]
  (if (:entity/state components)
    (update components :entity/state
            (fn [[player-or-npc initial-state]]
              {:initial-state initial-state
               :fsm (case player-or-npc
                      :state/player fsms/player
                      :state/npc    fsms/npc)}))
    components))

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
            (safe-merge (set-fsm components)))]])))
