(ns editor.widgets
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [component.db :as db]
            [component.info :as info]
            [component.property :as property]
            [editor.widget :as widget]
            [editor.overview :refer [overview-table]]
            [editor.utils :refer [scrollable-choose-window]]
            [gdx.graphics :as g]
            [gdx.assets :as assets]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.stage-screen :refer [stage-add!]]
            [utils.core :refer [truncate ->edn-str]])
  (:load "widgets_relationships"))

(def ^:private textfield->text com.kotcrab.vis.ui.widget.VisTextField/.getText)

(defn- add-schema-tooltip! [widget schema]
  (ui/add-tooltip! widget (str schema))
  widget)

;;

(defmethod widget/create :default [_ v]
  (ui/label (truncate (->edn-str v) 60)))

(defmethod widget/value :default [_ widget]
  ((a/id widget) 1))

;;

(defmethod widget/create :boolean [_ checked?]
  (assert (boolean? checked?))
  (ui/check-box "" (fn [_]) checked?))

(defmethod widget/value :boolean [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

;;

(defmethod widget/create :string [schema v]
  (add-schema-tooltip! (ui/text-field v {}) schema))

(defmethod widget/value :string [_ widget]
  (textfield->text widget))

;;

(defmethod widget/create number? [schema v]
  (add-schema-tooltip! (ui/text-field (->edn-str v) {}) schema))

(defmethod widget/value number? [_ widget]
  (edn/read-string (textfield->text widget)))

;;

(defmethod widget/create :enum [schema v]
  (ui/select-box {:items (map ->edn-str (rest schema))
                  :selected (->edn-str v)}))

(defmethod widget/value :enum [_ widget]
  (edn/read-string (.getSelected ^com.kotcrab.vis.ui.widget.VisSelectBox widget)))

;;

(defmethod db/edn->value :s/image [_ image]
  (g/edn->image image))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows []
  (for [file (sort assets/all-texture-files)]
    [(ui/image-button (g/image file) (fn []))]
    #_[(ui/text-button file (fn []))]))

(defn- big-image-button [image]
  (ui/image-button (g/edn->image image)
                   (fn on-clicked [])
                   {:scale 2}))

(defmethod widget/create :s/image [_ image]
  (big-image-button image)
  #_(ui/image-button image
                     #(stage-add! (scrollable-choose-window (texture-rows)))
                     {:dimensions [96 96]})) ; x2  , not hardcoded here

;;

(defmethod widget/create :s/animation [_ animation]
  (ui/table {:rows [(for [image (:frames animation)]
                      (big-image-button image))]
             :cell-defaults {:pad 1}}))

;;


(defn- ->play-sound-button [sound-file]
  (ui/text-button "play!" #(assets/play-sound! sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [table]
  (let [rows (for [sound-file assets/all-sound-files]
               [(ui/text-button (str/replace-first sound-file "sounds/" "")
                                (fn []
                                  (ui/clear-children! table)
                                  (ui/add-rows! table [(->sound-columns table sound-file)])
                                  (a/remove! (ui/find-ancestor-window ui/*on-clicked-actor*))
                                  (ui/pack-ancestor-window! table)
                                  (a/set-id! table sound-file)))
                (->play-sound-button sound-file)])]
    (stage-add! (scrollable-choose-window rows))))

(defn- ->sound-columns [table sound-file]
  [(ui/text-button (name sound-file) #(open-sounds-window! table))
   (->play-sound-button sound-file)])

(defmethod widget/create :s/sound [_ sound-file]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (ui/add-rows! table [(if sound-file
                           (->sound-columns table sound-file)
                           [(ui/text-button "No sound" #(open-sounds-window! table))])])
    table))
