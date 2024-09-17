(ns components.data.image
  (:require [gdx.scene2d.ui :as ui]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            core.animation
            core.image))

(defcomponent :image
  {:schema [:map {:closed true}
            [:file :string]
            [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})

(defmethod data/edn->value :image [_ image ctx]
  (core.image/edn->image image ctx))

(defcomponent :animation
  {:schema [:map {:closed true}
            [:frames :some]
            [:frame-duration pos?]
            [:looping? :boolean]]})

(defmethod data/edn->value :animation [_ animation ctx]
  (core.animation/edn->animation animation ctx))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (ctx/all-texture-files ctx))]
    [(ui/->image-button ctx (ctx/create-image ctx file) identity)]
    #_[(ui/->text-button ctx file identity)]))

(defmethod data/->widget :image [_ image ctx]
  (ui/->image-widget (core.image/edn->image image ctx) {})
  #_(ui/->image-button ctx image
                        #(ctx/add-to-stage! % (->scrollable-choose-window % (texture-rows %)))
                        {:dimensions [96 96]})) ; x2  , not hardcoded here

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod data/->widget :animation [_ animation ctx]
  (ui/->table {:rows [(for [image (:frames animation)]
                        (ui/->image-widget (core.image/edn->image image ctx) {}))]
               :cell-defaults {:pad 1}}))
