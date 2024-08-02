(ns properties.creature
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.properties :as properties]))

(defcomponent :properties/creature {}
  (properties/create [_]
    (defcomponent :creature/species {:widget :label :schema [:qualified-keyword {:namespace :species}]})
    (defcomponent :creature/level {:widget :text-field :schema [:maybe pos-int?]})
    (defcomponent :creature/entity (data/components
                                     [:entity/animation
                                      :entity/body
                                      :entity/faction
                                      :entity/flying?
                                      :entity/movement
                                      :entity/reaction-time
                                      :entity/hp
                                      :entity/mana
                                      :entity/inventory
                                      :entity/skills
                                      :entity/stats]))
    {:id-namespace "creatures"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :creatures}]]
              [:property/image
               :creature/species
               :creature/level
               :creature/entity])
     :edn-file-sort-order 1
     :overview {:title "Creatures"
                :columns 16
                :image/dimensions [65 65]
                :sort-by-fn #(vector (or (:creature/level %) 9)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %)
                                       (case (:entity/faction (:creature/entity %))
                                         :good "g"
                                         :evil "e"))}
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
                (when (seq inventory) (str "Items: " (str/join "," (map name inventory))))])}))
