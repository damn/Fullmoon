(ns core.tool
  (:require [clojure.java.io :as io])
  (:import (javafx.event EventHandler)
           (javafx.scene.control Button)
           (javafx.scene.image Image)
           (javafx.scene.layout StackPane)
           (javafx.scene Scene))
  (:gen-class
   :extends javafx.application.Application))

(defn -start [app stage]
  (let [btn-event-handler (proxy [EventHandler] []
                            (handle [event] (println "Hello World!")))
        btn (doto (Button.)
              (.setText "Say 'Hello World'")
              (.setOnAction btn-event-handler))
        root (StackPane.)
        scene (Scene. root 300 250)]
    (do
     (.add (.getChildren root) btn)
     (doto stage
       (-> .getIcons
           (.add (Image. (io/input-stream
                          (io/resource "images/moon_background.png")))))
       (.setTitle "Hello World !")
       (.setScene scene)
       (.show)))))

(defn -main [& args]
  (javafx.application.Application/launch core.tool
                                         (into-array String args)))
