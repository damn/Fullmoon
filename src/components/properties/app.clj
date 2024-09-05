(ns components.properties.app
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :fps {:data :nat-int})
(defcomponent :full-screen? {:data :boolean})
(defcomponent :width {:data :nat-int})
(defcomponent :height {:data :nat-int})
(defcomponent :title {:data :string})

(defcomponent :app/lwjgl3 {:data [:map [:fps :full-screen? :width :height :title]]})

; screens require vis-ui / properties (map-editor, property editor uses properties)
; properties requires graphics (image)
; so cannot be [:components-ns :context] map but has to be a vector
;= > cannot change anything then VISUI not loaded.. then VISUI not loaded..
(defcomponent :app/context {:data [:map
                                   [:context/assets
                                    :context/background-image
                                    :context/config
                                    :context/graphics
                                    :context/properties
                                    :context/screens
                                    [:context/vis-ui {:optional true}]
                                    [:context/tiled-map-renderer {:optional true}]]]})

(defcomponent :properties/app
  (component/create [_ _ctx]
    {:schema [:app/lwjgl3
              :app/context]
     :overview {:title "Apps"
                :columns 10}}))
