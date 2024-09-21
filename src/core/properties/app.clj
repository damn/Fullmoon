(ns core.properties.app
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [core.property :refer [def-property-type]]))

(data/def-attributes
  :fps          :nat-int
  :full-screen? :boolean
  :width        :nat-int
  :height       :nat-int
  :title        :string)

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
