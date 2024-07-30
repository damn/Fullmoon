(ns context.builder
  (:require [core.component :as component]
            gdl.context
            [api.context :refer [transact! get-property]]
            [api.entity :as entity]))

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

(extend-type gdl.context.Context
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
