(ns dev.info-tree
  (:require [core.app :as app]
            [core.context :as ctx]
            [gdx.scene2d.group :as group])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.scenes.scene2d.ui.Tree$Node
           com.kotcrab.vis.ui.widget.VisTree))

(comment

 (.postRunnable Gdx/app (fn [] (show-tree-view! :ctx)))
 (show-tree-view! :entity)
 (show-tree-view! :tile)

 )

; TODO expand only on click ... save bandwidth ....
; crash only on expanding big one ...

(defn- ->node [actor]
  (proxy [Tree$Node] [actor]))

(defn- ->tree []
  (VisTree.))

(defn ->v-str [v]
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

(defn ->labelstr [k v]
  (str "[LIGHT_GRAY]:"
       (if (keyword? k)
         (str
          (when-let [ns (namespace k)] (str ns "/")) "[WHITE]" (name k))
         k) ; TODO truncate ...
       ": [GOLD]" (str (->v-str v))))

(defn- add-elements! [ctx node elements]
  (doseq [element elements
          :let [el-node (->node (ctx/->label ctx (str (->v-str element))))]]
    (.add node el-node)))

(declare add-map-nodes!)

(defn- children->str-map [children]
  (zipmap (map str children)
          children))

(defn- ->nested-nodes [ctx node level v]
  (when (map? v)
    (add-map-nodes! ctx node v (inc level)))

  (when (and (vector? v) (>= (count v) 3))
    (add-elements! ctx node v))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Stage v)
    (add-map-nodes! ctx node (children->str-map (group/children (.getRoot v))) level))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Group v)
    (add-map-nodes! ctx node (children->str-map (group/children v)) level))
  )

(comment
 (let [vis-image (first (group/children (.getRoot (ctx/get-stage @app/state))))]
   (supers (class vis-image))
   (str vis-image)
   )
 )

(defn add-map-nodes! [ctx parent-node m level]
  ;(println "Level: " level " - go deeper? " (< level 4))
  (when (< level 2)
    (doseq [[k v] (into (sorted-map) m)]
      ;(println "add-map-nodes! k " k)
      (try
       (let [node (->node (ctx/->label ctx (->labelstr k v)))]
         (.add parent-node node)
         #_(when (instance? clojure.lang.Atom v) ; StackOverFLow
           (->nested-nodes ctx node level @v))
         (->nested-nodes ctx node level v))
       (catch Throwable t
         (throw (ex-info "" {:k k :v v} t))
         #_(.add parent-node (->node (->label ctx (str "[RED] "k " - " t))))

         )))))

(defn- ->prop-tree [ctx prop]
  (let [tree (->tree)]
    (add-map-nodes! ctx tree prop 0)
    tree))

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (ctx/->table ctx {:rows rows
                            :cell-defaults {:pad 1}
                            :pack? true})
        scroll-pane (ctx/->scroll-pane ctx table)]
    {:actor scroll-pane
     :width (/ (ctx/gui-viewport-width ctx) 2)
     :height
     (- (ctx/gui-viewport-height ctx) 50)
     #_(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))}))

(defn show-tree-view! [obj]
  (let [ctx @app/state
        object (case obj
                 :ctx ctx
                 :entity (ctx/mouseover-entity* ctx)
                 :tile @(get (ctx/world-grid ctx) (mapv int (ctx/world-mouse-position ctx))))]
    (ctx/add-to-stage! ctx
                       (ctx/->window ctx {:title "Tree View"
                                          :close-button? true
                                          :close-on-escape? true
                                          :center? true
                                          :rows [[(->scroll-pane-cell ctx [[(->prop-tree ctx (into (sorted-map) object))]])]]
                                          :pack? true}))))
