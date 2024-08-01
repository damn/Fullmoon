(ns app
  (:require ;; property-type dependencies ....
            [clojure.string :as str]
            [malli.core :as m]
            [utils.core :refer [readable-number]]
            [core.component :as component]
            [core.data :as attr]
            ; these 3 here because nstools needs to know to load them before
            effect.all
            entity.all
            modifier.all
            [api.context :refer [modifier-text effect-text]]
            ;;
            [app.libgdx.app :as app]))

; TODO if I load these properties which depend on effect/ entity/ modifier/
; on ctx/create and not here
; then we can move the dependency to effect/ entity/ and modifier/ out of this ns...

(component/def :property/image       attr/image)
(component/def :property/sound       attr/sound)
(component/def :property/pretty-name attr/string-attr)

(component/def :creature/species {:widget :label      :schema [:qualified-keyword {:namespace :species}]}) ; TODO not used ... but one of?
(component/def :creature/level   {:widget :text-field :schema [:maybe pos-int?]}) ; pos-int-attr ? ; TODO creature lvl >0, <max-lvls (9 ?)
; TODO what components required? got some without attack !
; also
; rename property/creature
(component/def :property/entity (attr/components-attribute :entity))


; component to text
; modifier add/remove
; item 'upgrade' colorless to sword fire
(component/def :item/modifier (attr/components-attribute :modifier))
(component/def :item/slot     {:widget :label :schema [:qualified-keyword {:namespace :inventory.slot}]}) ; TODO one of ... == 'enum' !!


(component/def :skill/start-action-sound       attr/sound)
(component/def :skill/action-time-modifier-key (attr/enum :stats/cast-speed :stats/attack-speed))
(component/def :skill/action-time              attr/pos-attr)
(component/def :skill/cooldown                 attr/nat-int-attr)
(component/def :skill/cost                     attr/nat-int-attr)
(component/def :skill/effect (attr/components-attribute :effect))

(component/def :hit-effect   (attr/components-attribute :effect))

(component/def :world/map-size       attr/pos-int-attr)
(component/def :world/max-area-level attr/pos-int-attr) ; TODO <= map-size !?
(component/def :world/spawn-rate     attr/pos-attr) ; TODO <1 !

; TODO make misc is when no property-type matches ? :else case?

; TODO similar to map-attribute & components-attribute
(defn- map-attribute-schema [id-attribute attr-ks]
  (m/schema
   (vec (concat [:map {:closed true} id-attribute] ; TODO same id-attribute w. different namespaces ...
                ; creature/id ?
                ; item/id ?
                (for [k attr-ks]
                  (vector k (:schema (get core.component/attributes k))))))))

(comment
 (defn- all-text-colors []
   (let [colors (seq (.keys (com.badlogic.gdx.graphics.Colors/getColors)))]
     (str/join "\n"
               (for [colors (partition-all 4 colors)]
                 (str/join " , " (map #(str "[" % "]" %) colors)))))))

(com.badlogic.gdx.graphics.Colors/put "ITEM_GOLD"
                                      (com.badlogic.gdx.graphics.Color. (float 0.84)
                                                                        (float 0.8)
                                                                        (float 0.52)
                                                                        (float 1)))

(com.badlogic.gdx.graphics.Colors/put "MODIFIER_BLUE"
                                      (com.badlogic.gdx.graphics.Color. (float 0.38)
                                                                        (float 0.47)
                                                                        (float 1)
                                                                        (float 1)))

(def ^:private skill-cost-color "[CYAN]")
(def ^:private action-time-color "[GOLD]")
(def ^:private cooldown-color "[SKY]")
(def ^:private effect-color "[CHARTREUSE]")
(def ^:private modifier-color "[VIOLET]")

(def ^:private property-types
  {:property.type/creature {:of-type? :creature/species
                            :edn-file-sort-order 1
                            :title "Creature"
                            :overview {:title "Creatures"
                                       :columns 16
                                       :image/dimensions [65 65]
                                       :sort-by-fn #(vector (or (:creature/level %) 9)
                                                            (name (:creature/species %))
                                                            (name (:property/id %)))
                                       :extra-info-text #(str (:creature/level %)
                                                              (case (:entity/faction (:property/entity %))
                                                                :good "g"
                                                                :evil "e"))}
                            :schema (map-attribute-schema
                                     [:property/id [:qualified-keyword {:namespace :creatures}]]
                                     [:property/image
                                      :creature/species
                                      :creature/level
                                      :property/entity])
                            :->text (fn [_ctx
                                         {:keys [property/id
                                                 creature/species
                                                 entity/flying?
                                                 entity/skills
                                                 entity/inventory
                                                 creature/level]}]
                                      [(str/capitalize (name id))
                                       (str/capitalize (name species))
                                       (when level (str "Level: " level))
                                       (str "Flying? " flying?)
                                       (when (seq skills) (str "Spells: " (str/join "," (map name skills))))
                                       (when (seq inventory) (str "Items: " (str/join "," (map name inventory))))])}

   :property.type/skill {:of-type? :skill/effect
                         :edn-file-sort-order 0
                         :title "Skill"
                         :overview {:title "Skill"
                                    :columns 16
                                    :image/dimensions [70 70]}
                         :schema (map-attribute-schema
                                  [:property/id [:qualified-keyword {:namespace :skills}]]
                                  [:property/image
                                   :skill/action-time
                                   :skill/cooldown
                                   :skill/cost
                                   :skill/effect
                                   :skill/start-action-sound
                                   :skill/action-time-modifier-key])
                         :->text (fn [ctx {:keys [property/id
                                                  skill/action-time
                                                  skill/cooldown
                                                  skill/cost
                                                  skill/effect
                                                  skill/action-time-modifier-key]}]
                                   [(str/capitalize (name id))
                                    (str skill-cost-color "Cost: " cost "[]")
                                    (str action-time-color
                                         (case action-time-modifier-key
                                           :stats/cast-speed "Casting-Time"
                                           :stats/attack-speed "Attack-Time")
                                         ": "
                                         (readable-number action-time) " seconds" "[]")
                                    (str cooldown-color "Cooldown: " (readable-number cooldown) "[]")
                                    (str effect-color (effect-text ctx effect) "[]")])}

   :property.type/item {:of-type? :item/slot
                        :edn-file-sort-order 3
                        :title "Item"
                        :overview {:title "Items"
                                   :columns 17
                                   :image/dimensions [60 60]
                                   :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                                          (name slot)
                                                          "")
                                                        (name (:property/id %)))}
                        :schema (map-attribute-schema
                                 [:property/id [:qualified-keyword {:namespace :items}]]
                                 [:property/pretty-name
                                  :property/image
                                  :item/slot
                                  :item/modifier])
                        :->text (fn [ctx
                                     {:keys [property/pretty-name
                                             item/modifier]
                                      :as item}]
                                  [(str "[ITEM_GOLD]" pretty-name (when-let [cnt (:count item)] (str " (" cnt ")")) "[]")
                                   (when (seq modifier) (str modifier-color (modifier-text ctx modifier) "[]"))])}

   ; TODO schema missing here .... world/princess key not at defattribute ... require schema ...
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
                                   :image/dimensions [96 96]}}})

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
