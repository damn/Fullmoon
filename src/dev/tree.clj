(ns dev.tree
  (:require [gdx.graphics :as g]
            [gdx.ui :as ui]
            [gdx.ui.stage :as stage]
            [gdx.ui.stage-screen :refer [stage-add!]]
            [world.core :as world :refer [mouseover-entity]]))

(comment

 (show-tree-view! :entity)
 (show-tree-view! :tile)
 )

(defn- ->v-str [v]
  (cond
   (number? v) v
   (keyword? v) v
   (string? v) (pr-str v)
   (boolean? v) v
   (instance? clojure.lang.Atom v) (str "[LIME] Atom [GRAY]" (class @v) "[]")
   (map? v) (str (class v))
   (and (vector? v) (< (count v) 3)) (pr-str v)
   (vector? v) (str "Vector "(count v))
   :else (str "[GRAY]" (str v) "[]")))

(defn- labelstr [k v]
  (str "[LIGHT_GRAY]:"
       (if (keyword? k)
         (str
          (when-let [ns (namespace k)] (str ns "/")) "[WHITE]" (name k))
         k) ; TODO truncate ...
       ": [GOLD]" (str (->v-str v))))

(defn- add-elements! [node elements]
  (doseq [element elements
          :let [el-node (ui/t-node (ui/label (str (->v-str element))))]]
    (.add node el-node)))

(declare add-map-nodes!)

(defn- children->str-map [children]
  (zipmap (map str children)
          children))

(defn- ->nested-nodes [node level v]
  (when (map? v)
    (add-map-nodes! node v (inc level)))

  (when (and (vector? v) (>= (count v) 3))
    (add-elements! node v))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Stage v)
    (add-map-nodes! node (children->str-map (ui/children (stage/root v))) level))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Group v)
    (add-map-nodes! node (children->str-map (ui/children v)) level)))

(comment
 (let [vis-image (first (ui/children (stage/root (stage-get))))]
   (supers (class vis-image))
   (str vis-image)
   )
 )

(defn- add-map-nodes! [parent-node m level]
  ;(println "Level: " level " - go deeper? " (< level 4))
  (when (< level 2)
    (doseq [[k v] (into (sorted-map) m)]
      ;(println "add-map-nodes! k " k)
      (try
       (let [node (ui/t-node (ui/label (labelstr k v)))]
         (.add parent-node node) ; no t-node-add!: tree cannot be casted to tree-node ... , Tree itself different .add
         #_(when (instance? clojure.lang.Atom v) ; StackOverFLow
           (->nested-nodes node level @v))
         (->nested-nodes node level v))
       (catch Throwable t
         (throw (ex-info "" {:k k :v v} t))
         #_(.add parent-node (ui/t-node (ui/label (str "[RED] "k " - " t))))

         )))))

(defn- ->prop-tree [prop]
  (let [tree (ui/tree)]
    (add-map-nodes! tree prop 0)
    tree))

(defn- scroll-pane-cell [rows]
  (let [table (ui/table {:rows rows
                         :cell-defaults {:pad 1}
                         :pack? true})
        scroll-pane (ui/scroll-pane table)]
    {:actor scroll-pane
     :width (/ (g/gui-viewport-width) 2)
     :height
     (- (g/gui-viewport-height) 50)
     #_(min (- (g/gui-viewport-height) 50) (height table))}))

(defn- show-tree-view! [obj]
  (let [object (case obj
                 :entity (mouseover-entity)
                 :tile @(get world/grid (mapv int (g/world-mouse-position))))]
    (stage-add! (ui/window {:title "Tree View"
                            :close-button? true
                            :close-on-escape? true
                            :center? true
                            :rows [[(scroll-pane-cell [[(->prop-tree (into (sorted-map) object))]])]]
                            :pack? true}))
    nil))
