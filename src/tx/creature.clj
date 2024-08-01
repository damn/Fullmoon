(ns tx.creature
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]))

; * all entities give inventory -> can push up friendlies .... show bag symbol or some dot if they have items
; or even create enemies w. inventory ....

; position
; body
; state (controller!)
; faction

; creature properties (not entity!)
; * animation
; * body
; * flying

; * faction
; * movement
; * reaction-time ???
; * hp
; * mana
; * inventory
; * skills
; !!! _ stats required _  !!! (and schema check etc.)

; * also NAME text , maybe even generate a 'name' and 'history' with chatgpt of stuff?

; all components also:
; * modifier
; * effects
; * to text info
; * editor widget
; * documentation

; and all data/components documentation visible (markdown?)

; => generate from 'data/type' the effects/modifiers ( e.g. entity/animation can be changed the color of the animaion with gdx-color)
; => e.g. a skill can be upgraded and the sound changed
; e.g. val-max , or entity/state different

; context.game.* make

; inventory/actionbar/windows move out of context -> can be accessed through a fn

(defmethod transact! :tx/creature [[_ creature-id extra-components] ctx]
  (let [entity-components (:property/entity (ctx/get-property ctx creature-id))]
    [[:tx/create (merge entity-components
                        extra-components
                        {:entity/z-order (if (:entity/flying? entity-components)
                                           :z-order/flying
                                           :z-order/ground)}
                        (when (= creature-id :creatures/lady-a)
                          {:entity/clickable {:type :clickable/princess}}))]]))
