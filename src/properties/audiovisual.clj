(ns properties.audiovisual
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :as ctx]
            [api.properties :as properties]
            [api.effect :as effect]))

(defcomponent :properties/audiovisual {}
  (properties/create [_]
    {:id-namespace "audiovisuals"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :audiovisuals}]]
              [:property/sound
               :property/animation])
     :edn-file-sort-order 7 ; ? global put
     :overview {:title "Audiovisuals"
                :columns 10
                :image/dimensions [96 96]} ; ??
     }))

(defmethod effect/do! :tx.entity/audiovisual [[_ position id] ctx]
  ; assert property of type audiovisual
  (let [{:keys [property/sound
                property/animation]} (ctx/get-property ctx id)]
    [[:tx/sound sound]
     [:tx/create #:entity {:body {:position position
                                  :width 0.5
                                  :height 0.5
                                  :z-order :z-order/effect}
                           :animation animation
                           :delete-after-animation-stopped? true}]]))
