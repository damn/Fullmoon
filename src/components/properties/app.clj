(ns components.properties.app
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :fps {:data :nat-int})
(defcomponent :full-screen? {:data :boolean})
(defcomponent :width {:data :nat-int})
(defcomponent :height {:data :nat-int})
(defcomponent :title {:data :string})

; TODO just use sort-by-order and define context-order
; e.g. just screens after the rest...

; screens require vis-ui / properties (map-editor, property editor uses properties)
; properties requires graphics (image)
; so cannot be [:components-ns :context] map but has to be a vector
;= > cannot change anything then VISUI not loaded.. then VISUI not loaded..
; if I save something else app/context will get messed up right? so has to be vector?
(defcomponent :app/context
  {:data :some
   #_[:map
      [:context/assets ; no deps
       :context/config ; no deps
       :context/graphics ; no deps
       :context/properties ; no deps
       :context/screens ; ? => properties, graphics, vis-ui, bg-image => move out of context?! but its ctx...
       ; screens as properties actually?!?!
       ; or vis-ui in graphics & tiled-map-renderer ?
       [:context/vis-ui {:optional true}] ; no deps
       [:context/tiled-map-renderer {:optional true}] ; no deps (but use graphics)
       ]]})

(defcomponent :properties/app
  (component/create [_ _ctx]
    {:schema [:app/lwjgl3
              :app/context]
     :overview {:title "Apps"
                :columns 10}}))
