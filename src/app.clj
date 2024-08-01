(ns app
  (:require ;; world property dep ....
            [core.component :as component]
            [core.data :as data]
            properties.property
            properties.creature
            properties.skill
            properties.item
            [app.libgdx.app :as app]))

(comment
 (defn- all-text-colors []
   (let [colors (seq (.keys (com.badlogic.gdx.graphics.Colors/getColors)))]
     (str/join "\n"
               (for [colors (partition-all 4 colors)]
                 (str/join " , " (map #(str "[" % "]" %) colors)))))))

(component/def :world/map-size       data/pos-int-attr)
(component/def :world/max-area-level data/pos-int-attr) ; TODO <= map-size !?
(component/def :world/spawn-rate     data/pos-attr) ; TODO <1 !

; TODO make misc is when no property-type matches ? :else case?

(def ^:private property-types
  (merge
   properties.creature/definition
   properties.skill/definition
   properties.item/definition
   {; TODO schema missing here .... world/princess key not at defattribute ... require schema ...
    :property.type/world {:of-type? :world/princess
                          :edn-file-sort-order 5
                          :title "World"
                          :overview {:title "Worlds"
                                     :columns 10
                                     :image/dimensions [96 96]}
                          #_:schema #_(map-attribute-schema
                                       [:property/id [:qualified-keyword {:namespace :worlds}]]
                                       [:world/map-size
                                        :world/max-area-level
                                        :world/princess
                                        :world/spawn-rate])}

    :property.type/misc {:of-type? (fn [{:keys [entity/hp
                                                creature/species
                                                item/slot
                                                skill/effect
                                                world/princess]}]
                                     (not (or hp species slot effect princess)))
                         :edn-file-sort-order 6
                         :title "Misc"
                         :overview {:title "Misc"
                                    :columns 10
                                    :image/dimensions [96 96]}}}))

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

             [:context/property-types property-types]
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

             [:context/screens {:first-screen :screens/main-menu
                                :screens {:screens/game           true
                                          :screens/main-menu      true
                                          :screens/map-editor     true
                                          :screens/minimap        true
                                          :screens/options-menu   true
                                          :screens/property-editor true}}]

             [:tx/sound true]
             [:tx/player-modal true]

             [:context/error-modal true]

             ; game
             ;[:context/ecs]
             ;[:context/mouseover-entity]
             ;[:context/player-message]
             ;[:context/counter]
             ;[:context/game-paused?] ; (atom nil)
             ;[:context/game-logic-frame] ; (atom 0)


             ]})

(defn -main []
  (app/start app-config))
