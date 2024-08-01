(ns app
  (:require [app.libgdx.app :as app]))

; edit /add / remove components with an dev-app
; creates properly named namespaces then
; open files also on request??

(def ^:private app-config
  {:app {:title "Cyber Dungeon Quest"
         :width  1440
         :height 900
         :full-screen? false
         :fps 60}
   ; TODO forgot to add component w. create for property-types
   ; => all have to defined as components??? those w.o data ??? return nil???
   ; => remove 'if's from core.component ... specify outer layer
   ; pull stuff out.....

   ; TODO !!
   ; make without slash ... then can directly grep n-name and find this too ! missed some !
   ; no you dont miss with grep for '.', also we need namespaced keyword short names @ functions
   :context [[:context.libgdx/graphics {:tile-size 48
                                        :default-font {:file "exocet/films.EXL_____.ttf" :size 16}}]
             [:context.libgdx/assets true]
             [:context.libgdx/ui true]
             [:context.libgdx/input true]
             [:context.libgdx/image-drawer-creator true]
             [:context.libgdx/stage true]
             [:context.libgdx/tiled true]
             [:context.libgdx/ttf-generator true]
             [:context/config {:tag :dev
                               :configs {:prod {:map-editor? false
                                                :property-editor? false
                                                :debug-window? false
                                                :debug-options? false}
                                         :dev {:map-editor? true
                                               :property-editor? true
                                               :debug-window? true
                                               :debug-options? true}}}]
             [:effect/all true]
             [:entity/all true]
             [:modifier/all true]
             [:property/all true]
             [:context/property-types {:properties/audiovisual true
                                       :properties/creature true
                                       :properties/item true
                                       :properties/skill true
                                       :properties/world true}]
             [:context/properties {:file "resources/properties.edn"}]
             ; strange when finds the namespace but wrong name @ component definition
             ; but we want to support pure namespaces just behaviour no create fn
             ; what to do ?
             ; smoketest
             [:context/cursor true]
             [:context/builder true]
             [:context/effect true]
             [:context/modifier true]
             [:context/potential-fields true]
             [:context/render-debug true]
             [:context/transaction-handler true]

             [:context/inventory true]
             [:context/action-bar true]
             [:context/ecs true]
             [:context/game [:context/inventory
                             :context/action-bar
                             :context/uids-entities
                             :context/thrown-error
                             :context/game-paused
                             :context/game-logic-frame
                             :context/elapsed-game-time
                             :context/mouseover-entity
                             :context/player-message]]

             ; requires context/config (debug-windows)
             ; make asserts .... for all dependencies ... everywhere o.o
             ; TODO context.screens/background-image
             [:context/background-image "ui/moon_background.png"]
             [:context/screens {:first-screen :screens/main-menu
                                :screens {:screens/game           true
                                          :screens/main-menu      true
                                          :screens/map-editor     true
                                          :screens/minimap        true
                                          :screens/options-menu   true
                                          :screens/property-editor true}}]
             ; TODO tx/all ?
             [:tx/sound true]
             [:tx/player-modal true]
             [:context/error-modal true]]})

(defn -main []
  (app/start app-config))
