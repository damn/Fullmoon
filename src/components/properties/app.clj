(ns components.properties.app
  (:require [core.component :refer [defcomponent]]
            [core.property :refer [def-property-type]]))

(defcomponent :fps          {:data :nat-int})
(defcomponent :full-screen? {:data :boolean})
(defcomponent :width        {:data :nat-int})
(defcomponent :height       {:data :nat-int})
(defcomponent :title        {:data :string})

(defcomponent :app/lwjgl3 {:data [:map [:fps
                                        :full-screen?
                                        :width
                                        :height
                                        :title]]})

(defcomponent :app/context
  {:data [:map [:context/assets
                :context/config
                :context/graphics
                :context/screens
                [:context/vis-ui {:optional true}]
                [:context/tiled-map-renderer {:optional true}]]]})

(def-property-type :properties/app
  {:schema [:app/lwjgl3
            :app/context]
   :overview {:title "Apps"
              :columns 10}})
