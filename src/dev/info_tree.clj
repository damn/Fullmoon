(ns ^:no-doc dev.info-tree
  (:require [core.ctx :refer :all]
            [core.ctx.ui :as ui]
            [core.ui.group :as group]
            [core.graphics.views :refer [gui-viewport-width gui-viewport-height world-mouse-position]]
            [core.screens.stage :as stage])
  (:import com.badlogic.gdx.scenes.scene2d.ui.Tree$Node
           com.kotcrab.vis.ui.widget.VisTree))

(comment

 (.postRunnable gdx-app (fn [] (show-tree-view! :ctx)))
 (show-tree-view! :entity)
 (show-tree-view! :tile)

 ; if we remove ns we can remove it at tree view !!

 )

; TODO expand only on click ... save bandwidth ....
; crash only on expanding big one ...

(defn- ->node [actor]
  (proxy [Tree$Node] [actor]))

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

(defn- ->labelstr [k v]
  (str "[LIGHT_GRAY]:"
       (if (keyword? k)
         (str
          (when-let [ns (namespace k)] (str ns "/")) "[WHITE]" (name k))
         k) ; TODO truncate ...
       ": [GOLD]" (str (->v-str v))))

(defn- add-elements! [node elements]
  (doseq [element elements
          :let [el-node (->node (ui/->label (str (->v-str element))))]]
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
    (add-map-nodes! node (children->str-map (group/children (.getRoot v))) level))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Group v)
    (add-map-nodes! node (children->str-map (group/children v)) level))
  )

(comment
 (let [vis-image (first (group/children (.getRoot (stage/get @app-state))))]
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
       (let [node (->node (ui/->label (->labelstr k v)))]
         (.add parent-node node)
         #_(when (instance? clojure.lang.Atom v) ; StackOverFLow
           (->nested-nodes node level @v))
         (->nested-nodes node level v))
       (catch Throwable t
         (throw (ex-info "" {:k k :v v} t))
         #_(.add parent-node (->node (ui/->label (str "[RED] "k " - " t))))

         )))))

(defn- ->prop-tree [prop]
  (let [tree (VisTree.)]
    (add-map-nodes! tree prop 0)
    tree))

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (ui/->table {:rows rows
                           :cell-defaults {:pad 1}
                           :pack? true})
        scroll-pane (ui/->scroll-pane table)]
    {:actor scroll-pane
     :width (/ (gui-viewport-width ctx) 2)
     :height
     (- (gui-viewport-height ctx) 50)
     #_(min (- (gui-viewport-height ctx) 50) (actor/height table))}))

(defn show-tree-view! [obj]
  (let [ctx @app-state
        object (case obj
                 :ctx ctx
                 :entity (mouseover-entity* ctx)
                 :tile @(get (:context/grid ctx) (mapv int (world-mouse-position ctx))))]
    (stage/add-actor! ctx
                      (ui/->window {:title "Tree View"
                                    :close-button? true
                                    :close-on-escape? true
                                    :center? true
                                    :rows [[(->scroll-pane-cell ctx [[(->prop-tree (into (sorted-map) object))]])]]
                                    :pack? true}))))
