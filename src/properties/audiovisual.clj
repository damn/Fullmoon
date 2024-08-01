(ns properties.audiovisual
  (:require [core.component :as component]
            [core.data :as data]))

(def definition
  {:property.type/audiovisual
   {:of-type? :property/animation ; hmmmm ... what now ? doesn;t work for audiovisuals ... add property/type ? or just type ?
    :edn-file-sort-order 7 ; ? global put
    :title "Audiovisual" ; forget it use keywords simpler
    :overview {:title "Audiovisuals" ; forget it use keywords simpler
               :columns 10
               :image/dimensions [96 96]} ; ??
    :schema (data/map-attribute-schema
             [:property/id [:qualified-keyword {:namespace :audiovisuals}]]
             [:property/sound
              :property/animation])}})
