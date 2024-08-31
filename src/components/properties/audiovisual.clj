(ns components.properties.audiovisual
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :properties/audiovisual
  (component/create [_ _ctx]
    {:id-namespace "audiovisuals"
     :schema [[:property/id [:qualified-keyword {:namespace :audiovisuals}]]
              [:tx/sound
               :entity/animation]]
     :overview {:title "Audiovisuals"
                :columns 10
                :image/scale 2}}))

(defcomponent :tx/audiovisual
  (component/do! [[_ position id] ctx]
    (let [{:keys [tx/sound
                  entity/animation]} (ctx/property ctx id)]
      [[:tx/sound sound]
       [:tx/create
        position
        {:width 0.5
         :height 0.5
         :z-order :z-order/effect}
        #:entity {:animation animation
                  :delete-after-animation-stopped? true}]])))
