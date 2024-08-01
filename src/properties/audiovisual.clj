(ns properties.audiovisual
  (:require [core.component :as component]
            [core.data :as data]
            [api.properties :as properties]))

(component/def :properties/audiovisual {}
  _
  (properties/create [_]
    {:id-namespace "audiovisuals"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :audiovisuals}]]
              [:property/sound
               :property/animation])
     :edn-file-sort-order 7 ; ? global put
     :overview {:title "Audiovisuals"
                :columns 10
                :image/dimensions [96 96]} ; ??
     }))
