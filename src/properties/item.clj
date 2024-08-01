(ns properties.item
  (:require [core.component :as component]
            [core.data :as data]
            [api.context :as ctx]
            modifier.all))

; modifier add/remove
; item 'upgrade' colorless to sword fire
(component/def :item/modifier (data/components-attribute :modifier))
(component/def :item/slot     {:widget :label :schema [:qualified-keyword {:namespace :inventory.slot}]}) ; TODO one of ... == 'enum' !!

(com.badlogic.gdx.graphics.Colors/put "ITEM_GOLD"
                                      (com.badlogic.gdx.graphics.Color. (float 0.84)
                                                                        (float 0.8)
                                                                        (float 0.52)
                                                                        (float 1)))

#_(com.badlogic.gdx.graphics.Colors/put "MODIFIER_BLUE"
                                        (com.badlogic.gdx.graphics.Color. (float 0.38)
                                                                          (float 0.47)
                                                                          (float 1)
                                                                          (float 1)))

(def ^:private modifier-color "[VIOLET]")

(def definition
  {:property.type/item {:of-type? :item/slot
                        :edn-file-sort-order 3
                        :title "Item"
                        :overview {:title "Items"
                                   :columns 17
                                   :image/dimensions [60 60]
                                   :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                                          (name slot)
                                                          "")
                                                        (name (:property/id %)))}
                        :schema (data/map-attribute-schema
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
                                   (when (seq modifier) (str modifier-color (ctx/modifier-text ctx modifier) "[]"))])}}
  )
