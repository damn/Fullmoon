(ns ^:no-doc editor.javafx
  (:require [clojure.java.io :as io]
            [dev.javafx :as fx]
            utils.core)
  (:import (javafx.event EventHandler)
           (javafx.scene.control Button TreeItem TreeView)
           (javafx.scene.image Image ImageView)
           (javafx.scene.layout StackPane)
           (javafx.scene Scene Node))
  #_(:gen-class :extends javafx.application.Application))

(comment
 ; * remove comment at :gen-class
 ; * lein with-profile javafx repl
 ; * lein clean before doing `dev` again (ns refresh doesnt work with aot)
 (db/load! "properties.edn")
 (fx/init)
 (fx/run (properties-tabs))

 )

(defn -start [app stage]
  (def stage stage))

(import javafx.scene.control.TabPane
        javafx.scene.control.TabPane$TabClosingPolicy
        javafx.scene.control.Tab
        javafx.scene.control.Label
        javafx.scene.control.TextField
        javafx.scene.layout.VBox)

(import javafx.geometry.Insets)
(import javafx.scene.image.ImageView)
(import javafx.scene.image.Image)
(import javafx.scene.layout.FlowPane)
(import javafx.scene.layout.GridPane)

(import javafx.geometry.Rectangle2D)

; TODO
; https://docs.oracle.com/javafx/2/get_started/form.htm
; * new window w. form label, widget, Save, Cancel button
(import javafx.stage.Stage)

(defn- property-editor-window [property]
  (let [grid (doto (GridPane.)
               (.setPadding (Insets. 0 10 0 10))
               (.setVgap 10)
               (.setHgap 10))
        scene (Scene. grid 450 450)
        rows (atom -1)]
    (doseq [[k v] property
            :let [row (swap! rows inc)]]
      (.add grid (Label. (str k)) 0 row)
      (.add grid (TextField. (utils.core/->edn-str v)) 1 row))
    (doto (Stage.)
      (.setTitle (name (:property/id property)))
      (.setScene scene)
      .show)))

(require '[component.property :as property])
(require '[component.db :as db])

(def ->image (memoize (fn [file] (Image. file))))

(defn image-view [{:keys [file sub-image-bounds]}]
  (let [[x y w h] sub-image-bounds
        image-view (ImageView. (->image file))]
    (if sub-image-bounds
      (do (.setViewport image-view (Rectangle2D. x y w h))
          image-view)
      image-view)))

(defn- property->button [property]
  (let [image (property/->image property)
        button (if image
                 (Button. "" (image-view (property/->image property)))
                 (Button. (name (:property/id property))))]
    (.setOnAction button (reify EventHandler
                           (handle [_ e]
                             (println (name (:property/id property)))
                             (property-editor-window property)
                             )))
    button))

(defn overview-flow-pane [property-type]
  (let [flow (doto (FlowPane.)
               (.setPadding (Insets. 5 0 5 0))
               (.setVgap 4)
               (.setHgap 4)
               (.setPrefWrapLength 500)
               (.setStyle "-f-background-color: DAE6F3;"))
        {:keys [sort-by-fn
                extra-info-text
                columns
                image/scale]} (property/overview property-type)
        properties (db/all-raw property-type)
        properties (if sort-by-fn
                     (sort-by sort-by-fn properties)
                     properties)]
    (doseq [property properties]
      (.add (.getChildren flow) (property->button property)))
    flow))

(defn- properties-tabs []
  (let [tab-pane (doto (TabPane.)
                   (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE))]
    (doseq [property-type (sort (property/types))
            :let [tab (Tab. (:title (property/overview property-type))
                            (Label. "whre thsi labl is"))]]
      (.setContent tab (overview-flow-pane property-type))
      (.add (.getTabs tab-pane) tab))
    (let [scene (Scene. (VBox. (into-array Node [tab-pane])))]
      (.setScene stage scene))
    (.setTitle stage "JavaFX App")
    (.show stage)))
