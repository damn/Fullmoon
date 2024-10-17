(ns ^:no-doc editor.javafx
  (:require [clojure.java.io :as io]
            [dev.javafx :as fx])
  (:import (javafx.event EventHandler)
           (javafx.scene.control Button TreeItem TreeView)
           (javafx.scene.image Image ImageView)
           (javafx.scene.layout StackPane)
           (javafx.scene Scene Node))
  #_(:gen-class :extends javafx.application.Application))

(import javafx.scene.control.TabPane
        javafx.scene.control.TabPane$TabClosingPolicy
        javafx.scene.control.Tab
        javafx.scene.control.Label
        javafx.scene.layout.VBox)

(import javafx.geometry.Insets)
(import javafx.scene.image.ImageView)
(import javafx.scene.image.Image)
(import javafx.scene.layout.FlowPane)

(import javafx.geometry.Rectangle2D)

; TODO
; https://docs.oracle.com/javafx/2/get_started/form.htm
; * new window w. form label, widget, Save, Cancel button
(import javafx.stage.Stage)

(defn- property-editor-window [property]
  (let [scene (Scene. (FlowPane.) 450 450)
        stage (doto (Stage.)
                (.setTitle (name (:property/id property)))
                (.setScene scene)
                .show)]))

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

(declare stage)

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

(defn -start [app the-stage]
  (def stage the-stage)
  #_(tool-tree "src/"))

(comment
 ; lein with-profile tool repl
 ; lein clean before doing `dev` again (ns refresh doesnt work with aot)

 (db/load! "properties.edn")
 (fx/init)
 (fx/run (properties-tabs))

 )
