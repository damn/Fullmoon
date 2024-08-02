(ns tx.creature
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]))

;;;

;; added:
; * position
; * state => what is it doing ? what is it requiring ???
; how is it working ??
; # what does it do ?
; # what does it depend on ?
; # where is it used (as data or api)
;; player:
; * player?
; * free-skill-points
; * clickable
; * click-distance-tiles

;; spawn:
; faction

;; IN PROPERTIES:

; * faction ( -> can remove & set manually for opponents ?)
; * flying => remove for z-order ? -> directly give z-order then with only 2 options !?
; * movement
; * reaction-time ??? -> not necessary for player.... ?!
; * hp
; * mana
; * inventory (for npcs???, can push up friendlies .... )
; * skills
; !!! _ stats required _  ???!!! (and schema check etc.)

#_(data/components
 [:entity/animation
  :entity/body
  :entity/faction
  :entity/flying?
  :entity/movement
  :entity/reaction-time
  :entity/hp
  :entity/mana
  :entity/inventory
  :entity/skills
  :entity/stats])

; => make components WELL DEFINED  ( what do they do, where used?, which optional ? , ... ? depend on which ? )
; => what does 'solid?' mean ???
; => entity info text...

;; Add: name, species, level ?
; * generate name/ history w. chatgpt ?

(defmethod transact! :tx/creature [[_ creature-id extra-components] ctx]
  (let [entity-components (:creature/entity (ctx/get-property ctx creature-id))]
    [[:tx/create (merge entity-components
                        extra-components
                        {:entity/z-order (if (:entity/flying? entity-components)
                                           :z-order/flying
                                           :z-order/ground)}
                        (when (= creature-id :creatures/lady-a)
                          {:entity/clickable {:type :clickable/princess}}))]]))
