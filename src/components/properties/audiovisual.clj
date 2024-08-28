(ns components.properties.audiovisual
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :properties/audiovisual
  (component/create [_ _ctx]
    {:id-namespace "audiovisuals"
     :schema [[:property/id [:qualified-keyword {:namespace :audiovisuals}]]
              [:property/sound
               :entity/animation]]
     :edn-file-sort-order 7
     :overview {:title "Audiovisuals"
                :columns 10
                :image/dimensions [96 96]}}))

(defcomponent :tx.entity/audiovisual
  (component/do! [[_ position id] ctx]
    (let [{:keys [property/sound
                  entity/animation]} (ctx/get-property ctx id)]
      [[:tx/sound sound]
       [:tx/create
        {:position position
         :width 0.5
         :height 0.5
         :z-order :z-order/effect}
        #:entity {:animation animation
                  :delete-after-animation-stopped? true}]])))
