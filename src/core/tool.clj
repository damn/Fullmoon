(ns core.tool
  (:require [clojure.java.io :as io])
  (:import (javafx.event EventHandler)
           (javafx.scene.control Button TreeItem TreeView)
           (javafx.scene.image Image ImageView)
           (javafx.scene.layout StackPane)
           (javafx.scene Scene Node))
  (:gen-class
   :extends javafx.application.Application))

; https://stackoverflow.com/questions/66978726/clojure-javafx-live-manipulation
; https://github.com/dlsc-software-consulting-gmbh/FormsFX
; https://github.com/antoniopelusi/JavaFX-Dark-Theme?tab=readme-ov-file

(defn- clj-file-forms [file]
  (read-string (str "[" (slurp file) "]")))

(require '[rewrite-clj.parser :as p])

(comment
 (p/parse-file-all "src/core/stat.clj")

 (p/parse-file-all "../clojure.gdx/src/clojure/gdx/java.clj")


 )



#_(defn -start [app stage]
  (def myapp app)
  (.setTitle stage "Tree View Sample")
  (let [root-icon (ImageView. (Image. (io/input-stream
                                       (io/resource "images/animations/vampire-1.png"))))
        root-item (TreeItem. "Inbox", root-icon)]
    (.setExpanded root-item true)
    (doseq [
            ;[first-sym second-sym] (doall (map (partial take 2) (clj-file-forms "src/core/stat.clj")))

            file (map str (file-seq (io/file "src/")))
            ]
      (.add (.getChildren root-item) (TreeItem.
                                      file
                                      #_(str first-sym " - " second-sym))))

    (let [stack-pane (StackPane.)]
      (.add (.getChildren stack-pane) (TreeView. root-item))
      (.setScene stage (Scene. stack-pane 300 250))
      (.show stage))))

(import javafx.scene.control.TabPane
        javafx.scene.control.TabPane$TabClosingPolicy
        javafx.scene.control.Tab
        javafx.scene.control.Label
        javafx.scene.layout.VBox)

(import javafx.geometry.Insets)
(import javafx.scene.image.ImageView)
(import javafx.scene.image.Image)
(import javafx.scene.layout.FlowPane)

(defmacro fx-run
  "With this macro what you run is run in the JavaFX Application thread.
  Is needed for all calls related with JavaFx"
  [& code]
  `(javafx.application.Platform/runLater (fn [] ~@code)))


(import javafx.geometry.Rectangle2D)

(defn flow-pane []
  (let [flow (doto (FlowPane.)
               (.setPadding (Insets. 5 0 5 0))
               (.setVgap 4)
               (.setHgap 4)
               (.setPrefWrapLength 500)
               (.setStyle "-f-background-color: DAE6F3;"))
        ;["images/items.png" "images/skills.png"]
        image (Image. "images/creatures.png")
        size 48]
    (doseq [x (range 10)
            y (range 13)]
      (.add (.getChildren flow)
            (doto (Button.)
              (.setGraphic (doto (ImageView. image)
                              (.setViewport (Rectangle2D. (* x size) (* y size) size size)))))))
    flow))

(comment
 ; 480 x 624
 ; 10 width ( 48 )
 ; 13 height ( 48 )
 ; Rectangle2D imagePart = new Rectangle2D((imageNumber - 1) * IMAGE_WIDTH, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
 ; imageView.setViewport(imagePart);
 )

(declare my-stage)

(comment
 (fx-run
  (let [tab-pane (doto (TabPane.)
                   (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE))]
    (doseq [tab [(Tab. "Planes" (Label. "Show all planes"))
                 (Tab. "Cars" (Label. "Show all cars"))
                 (Tab. "Boats" (Label. "Show all boats"))]]
      (.setContent tab (flow-pane))
      (.add (.getTabs tab-pane) tab))
    (let [scene (Scene. (VBox. (into-array Node [tab-pane])))]
      ;(.add (.getStylesheets scene) "style.css")
      (.setScene my-stage scene))
    (.setTitle my-stage "JavaFX App")
    (.show my-stage)))
 )

(defn -start [app stage]
  (def my-stage stage))

(defn -main [& args]
  (javafx.application.Platform/setImplicitExit false)
  (.start (Thread. #(javafx.application.Application/launch core.tool (into-array String [""])))))
