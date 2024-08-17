(ns properties.item
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :as ctx]
            [api.properties :as properties]
            [api.tx :refer [transact!]]
            entity.stats))

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

(defn- modifier-text [modifiers]
  (str/join "\n"
            (for [[modifier-k operations] modifiers
                  operation operations]
              (entity.stats/info-text modifier-k operation))))

(defcomponent :properties/item {}
  (properties/create [_]

    (defcomponent :item/modifier ; TODO plural
      (data/components-attribute :modifier))

    (defcomponent :item/slot {:widget :label
                              :schema [:qualified-keyword {:namespace :inventory.slot}]}) ; TODO one of ... == 'enum' !!

    {:id-namespace "items"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :items}]]
              [:property/pretty-name
               :property/image
               :item/slot
               :item/modifier])
     :edn-file-sort-order 3
     :overview {:title "Items"
                :columns 17
                :image/dimensions [60 60]
                :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                       (name slot)
                                       "")
                                     (name (:property/id %)))}
     :->text (fn [ctx
                  {:keys [property/pretty-name
                          item/modifier]
                   :as item}]
               [(str "[ITEM_GOLD]" pretty-name (when-let [cnt (:count item)] (str " (" cnt ")")) "[]")
                (when (seq modifier)
                  (modifier-text modifier))])}))

; TODO use image w. shadows spritesheet
(defmethod transact! :tx.entity/item  [[_ position item] _ctx]
  (assert (:property/image item))
  [[:tx/create #:entity {:body {:position position
                                :width 0.5 ; TODO use item-body-dimensions
                                :height 0.5
                                :solid? false
                                :z-order :z-order/on-ground}
                         :image (:property/image item)
                         :item item
                         :clickable {:type :clickable/item
                                     :text (:property/pretty-name item)}}]])
