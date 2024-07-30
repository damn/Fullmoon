(ns app
  (:require [gdl.libgdx.app :as app]
            (context.ui player-modal
                        error-modal)))

(def ^:private app-config
  {:app {:title "Cyber Dungeon Quest"
         :width  1440
         :height 900
         :full-screen? false
         :fps 60}
   :context [[:gdl.libgdx.context/graphics {:tile-size 48
                                            :default-font {:file "exocet/films.EXL_____.ttf" :size 16}}]
             [:gdl.libgdx.context/assets true]
             [:gdl.libgdx.context/ui true]
             [:gdl.libgdx.context/input true]
             [:gdl.libgdx.context/image-drawer-creator true]
             [:gdl.libgdx.context/stage true]
             [:gdl.libgdx.context/tiled true]
             [:gdl.libgdx.context/ttf-generator true]

             [:context/config {:tag :dev
                               :configs {:prod {:map-editor? false
                                                :property-editor? false
                                                :debug-windows? false
                                                :debug-options? false}
                                         :dev {:map-editor? true
                                               :property-editor? true
                                               :debug-windows? true
                                               :debug-options? true}}}]

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
             [:context/action-bar true] ; fehlt

             ; requires context/config (debug-windows)
             ; make asserts .... for all dependencies ... everywhere o.o
             [:context/background-image "ui/moon_background.png"]

             [:gdl.context/screens {:first-screen :screens/main-menu
                                    :screens {:screens/game           true
                                              :screens/main-menu      true
                                              :screens/map-editor     true
                                              :screens/minimap        true
                                              :screens/options-menu   true
                                              :screens/property-editor true}}]]})

(defn -main []
  (app/start app-config))
