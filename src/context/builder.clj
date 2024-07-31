(ns context.builder
  (:require [core.component :as component]
            [api.context :refer [get-property]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

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
  (let [entity-components (:property/entity (get-property ctx creature-id))]
    [[:tx/create (merge entity-components
                        extra-components
                        {:entity/z-order (if (:entity/flying? entity-components)
                                           :z-order/flying
                                           :z-order/ground)}
                        (when (= creature-id :creatures/lady-a)
                          {:entity/clickable {:type :clickable/princess}}))]]))

(component/def :entity/plop {} _
  (entity/destroy [_ entity* ctx]
    [[:tx/audiovisual (:entity/position entity*) :projectile/hit-wall-effect]]))

(extend-type api.context.Context
  api.context/Builder
  ; TODO use image w. shadows spritesheet
  (item-entity [_ position item]
    #:entity {:position position
              :body {:width 0.5 ; TODO use item-body-dimensions
                     :height 0.5
                     :solid? false}
              :z-order :z-order/on-ground
              :image (:property/image item)
              :item item
              :clickable {:type :clickable/item
                          :text (:property/pretty-name item)}})

  (line-entity [_ {:keys [start end duration color thick?]}]
    #:entity {:position start
              :z-order :z-order/effect
              :line-render {:thick? thick? :end end :color color}
              :delete-after-duration duration}))

(defmethod transact! :tx/audiovisual [[_ position id] ctx]
  (let [{:keys [property/sound
                entity/animation]} (get-property ctx id)]
    [[:tx/sound sound]
     [:tx/create #:entity {:position position
                           :animation animation
                           :z-order :z-order/effect
                           :delete-after-animation-stopped? true}]]))
