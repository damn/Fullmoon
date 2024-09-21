(ns core.data.image
  (:require [gdx.scene2d.ui :as ui]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            core.image))

(defcomponent :image
  {:schema [:map {:closed true}
            [:file :string]
            [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})

(defmethod data/edn->value :image [_ image ctx]
  (core.image/edn->image image ctx))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (:texture-files (:context/assets ctx)))]
    [(ui/->image-button ctx (ctx/create-image ctx file) identity)]
    #_[(ui/->text-button ctx file identity)]))

(defmethod data/->widget :image [_ image ctx]
  (ui/->image-widget (core.image/edn->image image ctx) {})
  #_(ui/->image-button ctx image
                        #(ctx/add-to-stage! % (->scrollable-choose-window % (texture-rows %)))
                        {:dimensions [96 96]})) ; x2  , not hardcoded here
