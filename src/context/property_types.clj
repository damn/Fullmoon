(ns context.property-types
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [core.component :as component]
            [utils.core :refer [readable-number]]
            [cdq.api.context :refer [modifier-text effect-text]]
            [cdq.attributes :as attr]
            cdq.tx.all
            cdq.entity.all
            cdq.modifier.all))

(component/def :property/image       attr/image)
(component/def :property/sound       attr/sound)
(component/def :property/pretty-name attr/string-attr)

(component/def :property/entity (attr/components-attribute :entity))
(component/def :skill/effect (attr/components-attribute :tx))
(component/def :hit-effect   (attr/components-attribute :tx))
(component/def :item/modifier (attr/components-attribute :modifier))

(component/def :item/slot     {:widget :label :schema [:qualified-keyword {:namespace :inventory.slot}]}) ; TODO one of ... == 'enum' !!

(component/def :creature/species {:widget :label      :schema [:qualified-keyword {:namespace :species}]}) ; TODO not used ... but one of?
(component/def :creature/level   {:widget :text-field :schema [:maybe pos-int?]}) ; pos-int-attr ? ; TODO creature lvl >0, <max-lvls (9 ?)

(component/def :skill/start-action-sound       attr/sound)
(component/def :skill/action-time-modifier-key (attr/enum :stats/cast-speed :stats/attack-speed))
(component/def :skill/action-time              attr/pos-attr)
(component/def :skill/cooldown                 attr/nat-int-attr)
(component/def :skill/cost                     attr/nat-int-attr)

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



(defn- property->text [{:keys [context/property-types] :as ctx} property]
  ((:->text (get property-types (property->type property-types property)))
   ctx
   property))

; TODO property text is without effect-ctx .... handle that different .... ?
; maybe don't even need that @ editor ??? different lvl ...
; its basically ';component - join newlines & to text ... '
; generic thing for that ...

(extend-type gdl.context.Context
  cdq.api.context/TooltipText
  (tooltip-text [ctx property]
    (try (->> property
              (property->text ctx)
              (remove nil?)
              (str/join "\n"))
         (catch Throwable t
           (str t))))

  (player-tooltip-text [ctx property]
    (cdq.api.context/tooltip-text
     (assoc ctx :effect/source (:context/player-entity ctx))
     property)))

(extend-type gdl.context.Context
  cdq.api.context/PropertyTypes
  (of-type? [{:keys [context/property-types]} property-type property]
    ((:of-type? (get property-types property-type))
     property))

  (validate [{:keys [context/property-types]} property & {:keys [humanize?]}]
    (let [ptype (property->type property-types property)]
      (if-let [schema (:schema (get property-types ptype))]
        (if (try (m/validate schema property)
                 (catch Throwable t
                   (throw (ex-info "m/validate fail" {:property property :ptype ptype} t))))
          property
          (throw (Error. (let [explained (m/explain schema property)]
                           (str (if humanize?
                                  (me/humanize explained)
                                  (binding [*print-level* nil]
                                    (with-out-str
                                     (clojure.pprint/pprint
                                      explained)))))))))
        property)))

  (property->type [{:keys [context/property-types]} property]
    (some (fn [[type {:keys [of-type?]}]]
            (when (of-type? property)
              type))
          property-types))

  (edn-file-sort-order [{:keys [context/property-types]} property-type]
    (:edn-file-sort-order (get property-types property-type)))

  (overview [{:keys [context/property-types]} property-type]
    (-> property-types
        property-type
        :overview))

  (property-types [{:keys [context/property-types]}]
    (keys property-types)))
