(ns properties.creature
  (:require [clojure.string :as str]
            [core.component :as component]
            [core.data :as data]
            entity.all))

(component/def :creature/species {:widget :label      :schema [:qualified-keyword {:namespace :species}]}) ; TODO not used ... but one of?
(component/def :creature/level   {:widget :text-field :schema [:maybe pos-int?]}) ; pos-int-attr ? ; TODO creature lvl >0, <max-lvls (9 ?)
; TODO what components required? got some without attack !
; also
; rename property/creature
(component/def :property/entity (data/components-attribute :entity))

(def definition
  {:property.type/creature
   {:of-type? :creature/species
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
    :schema (data/map-attribute-schema
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
               (when (seq inventory) (str "Items: " (str/join "," (map name inventory))))])}})
