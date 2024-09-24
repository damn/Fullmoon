(ns ^:no-doc core.property.data.image
  (:require [core.ui :as ui]
            [core.ctx :refer :all]
            [core.property :as property]))

(defcomponent :image
  {:schema [:map {:closed true}
            [:file :string]
            [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})

(defmethod property/edn->value :image [_ image ctx]
  (edn->image image ctx))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (:texture-files (assets ctx)))]
    [(ui/->image-button (->image ctx file) identity)]
    #_[(ui/->text-button file identity)]))

(defmethod property/->widget :image [_ image ctx]
  (ui/->image-widget (edn->image image ctx) {})
  #_(ui/->image-button image
                       #(ui/stage-add! % (->scrollable-choose-window % (texture-rows %)))
                       {:dimensions [96 96]})) ; x2  , not hardcoded here
