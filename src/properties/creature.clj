(ns properties.creature
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :as ctx]
            [api.effect :as effect]
            [api.properties :as properties]
            [api.tx :refer [transact!]]
            [entity-state.fsms :as fsms]))

(import 'com.badlogic.gdx.graphics.g2d.TextureAtlas)

(comment
 (let [atlas (TextureAtlas. "creatures/creatures.atlas")
       region (.findRegion atlas "foo")
       ]

   ))

(defcomponent :properties/creature {}
  (properties/create [_]
    (defcomponent :creature/species {:widget :label :schema [:qualified-keyword {:namespace :species}]})
    (defcomponent :creature/level {:widget :text-field :schema [:maybe pos-int?]})
    (defcomponent :entity/flying? data/boolean-attr)
    (defcomponent :entity/reaction-time data/pos-attr)
    (defcomponent :creature/entity (data/components
                                     [:entity/animation
                                      :entity/body
                                      :entity/flying?
                                      :entity/reaction-time
                                      :entity/faction
                                      :entity/stats
                                      :entity/inventory
                                      :entity/skills]))
    {:id-namespace "creatures"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :creatures}]]
              [:property/image
               :creature/species
               :creature/level
               :creature/entity])
     :edn-file-sort-order 1
     :overview {:title "Creatures"
                :columns 15
                :image/dimensions [72 72]
                :sort-by-fn #(vector (:creature/level %)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %)
                                       (case (:entity/faction (:creature/entity %))
                                         :good "g"
                                         :evil "e"))}
     :->text (fn [_ctx
                  {:keys [property/id
                          creature/species
                          creature/level
                          creature/entity]}]
               [(str/capitalize (name id)) ; == pretty name
                (str "Species: " (str/capitalize (name species)))
                (when level (str "Level: " level))
                (binding [*print-level* nil]
                  (with-out-str
                   (clojure.pprint/pprint
                    (select-keys entity
                                 [;:entity/animation
                                  ;:entity/body
                                  :entity/faction
                                  :entity/flying?
                                  :entity/reaction-time
                                  :entity/inventory
                                  :entity/skills
                                  :entity/stats]))))])}))

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
     :state/player fsms/->player-state
     :state/npc fsms/->npc-state)
   initial-state))

; if controller = :controller/player
; -> add those fields
; :player? true ; -> api -> 'entity/player?' fn
; :free-skill-points 3
; :clickable {:type :clickable/player}
; :click-distance-tiles 1.5

; otherwise

(defmethod transact! :tx.entity/creature [[_ creature-id components] ctx]
  (assert (:entity/state components))
  (let [creature-components (:creature/entity (ctx/get-property ctx creature-id))]
    [[:tx/create
      (-> creature-components
          (dissoc :entity/flying?)
          (update :entity/body
                  (fn [body]
                    (-> body
                        (assoc :position (:entity/position components)) ; give position separate arg
                        (assoc :z-order (if (:entity/flying? creature-components)
                                          :z-order/flying
                                          :z-order/ground)))))
          (merge (dissoc components :entity/position)
                 (when (= creature-id :creatures/lady-a) ; do @ ?
                   {:entity/clickable {:type :clickable/princess}}))
          (update :entity/state set-state))]])) ; do @ entity/state itself

(comment

 (set! *print-level* nil)
 (clojure.pprint/pprint
  (transact! [:tx.entity/creature :creatures/vampire
              {:entity/position [1 2]
               :entity/state [:state/npc :sleeping]}]
             (reify api.context/PropertyStore
               (get-property [_ id]
                 {:creature/entity {:entity/flying? true
                                    :entity/body {:width 5
                                                  :height 5}}}))))

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
(defcomponent :effect/spawn {:widget :text-field
                             :schema [:qualified-keyword {:namespace :creatures}]}
  (effect/text [[_ creature-id] _effect-ctx]
    (str "Spawns a " (name creature-id)))

  (effect/valid-params? [_ {:keys [effect/source effect/target-position]}]
    ; TODO line of sight ? / not blocked ..
    (and source
         (:entity/faction @source)
         target-position))

  (transact! [[_ creature-id] {:keys [effect/source effect/target-position]}]
    [[:tx.entity/creature
      creature-id
      #:entity {:position target-position
                :state [:state/npc :idle]
                :faction (:entity/faction @source)}]]))
