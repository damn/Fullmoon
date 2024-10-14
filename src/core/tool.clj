(ns core.tool
  (:require [clojure.java.io :as io])
  (:import (javafx.event EventHandler)
           (javafx.scene.control Button TreeItem TreeView)
           (javafx.scene.image Image ImageView)
           (javafx.scene.layout StackPane)
           (javafx.scene Scene Node))
  (:gen-class :extends javafx.application.Application))

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

(comment
 (first (doall (map (partial take 2) (clj-file-forms "src/clojure/gdx.clj")))))

(declare stage)

(defn amount-syms [sym]
  (case (str sym)
    ("ns" "def" "declare" "defmacro" "defn" "defn-" "defmulti"
          "defsystem" "defc" "defrecord" "defprotocol" "deftype") 1
    ("defmethod" "derive") 2
    0))

(defn- first-sym->icon [sym]
  (case (str sym)
    "ns"          "📖"
    "comment"     "💬"
    "def"         "📌"
    "declare"     "📌"
    "defmacro"    "🏗️"
    "defn"        "🔧"
    "defn-"       "🔒🔧"
    "defmulti"    "🔧️"
    "defsystem"   "🔧️️"
    "defc"        "🧩"
    "defmethod"   "🧩"
    "derive"      "🧬"
    "defprotocol" "📄🔧️️"
    "defrecord"   "🗃️"
    "deftype"     "📦"
    "?"))

(require '[clojure.string :as str])

(defn- form->label [[first-sym & more]]
  (str (first-sym->icon first-sym)
       " "
       first-sym
       " "
       (when-let [amount (amount-syms first-sym)]
         (str/join " " (take amount more)))))

(defn- clj-files [folder]
  (sort (filter #(str/ends-with? % ".clj") (map str (file-seq (io/file "src/"))))))

; (clj-files "src/")

(defn- file->ns-str [path]
  (-> path
      (str/replace "src/" "")
      (str/replace ".clj" "")
      (str/replace "/" ".")))

(defn- file->pretty [file]
  (let [ns-str (file->ns-str file)]
    (str (when-let [icon (::icon (meta (find-ns (symbol ns-str))))]
           (str icon " "))
         ns-str)))

; (meta (find-ns (symbol (file->pretty "src/clojure/gdx/app.clj"))))

(defn- form->node [form]
  (let [tree-item (TreeItem. (form->label form))]
    #_(cond (= 'defprotocol (first form))
          (do
           (println "Found defprotocol : " (second form))
           (doseq [sig (sort (map name (keys (:sigs clojure.gdx.app/Listener))))]
             (.add (.getChildren tree-item) (TreeItem. (str (first-sym->icon 'defn) sig))))))
    tree-item))

(defn- file-forms-tree-item [file]
  (let [item (TreeItem. (file->pretty file))]
    (doseq [form (clj-file-forms file)]
      (.add (.getChildren item) (form->node form)))
    item))

;(.getName (.isDirectory (second (seq (.listFiles (io/file "src/"))))))

(defn add-items! [root-item root-file]
  (doseq [file (sort-by java.io.File/.getName (seq (.listFiles root-file)))
          :let [file-name (.getName file)
                path (.getPath file)]
          :when (not (str/starts-with? file-name "."))]
    (.add (.getChildren root-item)
          (cond
           (.isDirectory file)
           (let [tree-item (TreeItem. path)]
             (add-items! tree-item file)
             tree-item)

           (str/ends-with? file-name ".clj")
           (file-forms-tree-item path)

           :else
           (TreeItem. path)))))

(defn tool-tree [folder]
  (.setTitle stage "Tool")
  (let [root-item (TreeItem. folder)]
    (.setExpanded root-item true)
    (add-items! root-item (io/file "src/"))
    (let [stack-pane (StackPane.)]
      (.add (.getChildren stack-pane) (TreeView. root-item))
      (let [scene (Scene. stack-pane 400 900)]
        (.add (.getStylesheets scene) (.toExternalForm (io/resource "darkmode.css")))
        (.setScene stage scene))
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

(import javafx.geometry.Rectangle2D)

; TODO
; https://docs.oracle.com/javafx/2/get_started/form.htm
; * new window w. form label, widget, Save, Cancel button
(import javafx.stage.Stage)

(comment
 (fx-run
  (let [scene (Scene. (FlowPane.) 450 450)
        stage (doto (Stage.)
                (.setTitle "FOOBAR")
                (.setScene scene)
                .show)]))
 )

#_(comment
 (prop->image (first (all-properties-raw :properties/audiovisuals)))
 {:file "images/oryx_16bit_scifi_FX_lg_trans.png", :sub-image-bounds [64 64 32 32]}

 (def ->image-view (memoize (fn [file] (ImageView. (Image. file)))))

 (defn- ->image-view [{:keys [file sub-image-bounds]}]
   (let [imgview (->image-view file)]
     (if sub-image-bounds
       imgview
       )
     )
   (doto imgview
     (.setViewport (Rectangle2D. (* x size) (* y size) size size)))
   ))

(require '[core.property :as property])
(require '[core.db :as db])

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
        properties (db/all property-type)
        properties (if sort-by-fn
                     (sort-by sort-by-fn properties)
                     properties)]
    (doseq [property properties]
      (.add (.getChildren flow) #_(Button. (name (:property/id property)))
            (doto (Button. (:property/id property))
              #_(.setGraphic (doto (ImageView. image)
                             (.setViewport (Rectangle2D. (* x size) (* y size) size size)))))))
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

(defmacro fx-run
  "With this macro what you run is run in the JavaFX Application thread.
  Is needed for all calls related with JavaFx"
  [& code]
  `(javafx.application.Platform/runLater (fn [] ~@code)))

#_(when-not (= (System/getenv "DEV_MODE") "true")
  (gen-class
   :name "core.tool"
   :extends "javafx.application.Application"))

(defn- init-javafx! []
  (javafx.application.Platform/setImplicitExit false)
  ; otherwise cannot find class in dev mode w. ns-refresh, so create symbol
  (javafx.application.Application/launch (eval 'core.tool) (into-array String [""])))

(defn -main [& args]
  (init-javafx!))

(comment
 ; lein with-profile tool repl
 ; lein clean before doing `dev` again (ns refresh doesnt work with aot)

 (.start (Thread. init-javafx!))

 (fx-run (tool-tree "src/"))

 (fx-run (properties-tabs))

 )
