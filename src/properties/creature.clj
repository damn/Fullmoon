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
                :image/dimensions [72 72]
                :sort-by-fn #(vector (:creature/level %)
                                     (name (:creature/species %))
                                     (name (:property/id %)))
                :extra-info-text #(str (:creature/level %)
                                       (case (:entity/faction (:creature/entity %))
                                         :good "g"
                                         :evil "e"))}
     :->text (fn [_ctx
                  {:keys [property/id
                          creature/species
                          creature/level
                          creature/entity]}]
               [(str/capitalize (name id)) ; == pretty name
                (str "Species: " (str/capitalize (name species)))
                (when level (str "Level: " level))
                (binding [*print-level* nil]
                  (with-out-str
                   (clojure.pprint/pprint
                    (select-keys entity
                                 [;:entity/animation
                                  ;:entity/body
                                  :entity/faction
                                  :entity/flying?
                                  :entity/reaction-time
                                  :entity/hp
                                  :entity/mana
                                  :entity/inventory
                                  :entity/skills
                                  :entity/stats]))))])}))
