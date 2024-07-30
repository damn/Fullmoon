(ns dev
  (:require [clojure.pprint :refer :all]
            [clojure.string :as str]
            [gdl.context :as ctx :refer :all]
            [gdl.scene2d.actor :as actor]
            ))

(import 'com.badlogic.gdx.scenes.scene2d.ui.Tree$Node)
(import 'com.kotcrab.vis.ui.widget.VisTree)

(defn- ->node [actor]
  (proxy [Tree$Node] [actor]))

(defn- ->tree []
  (VisTree.))

(defn- expand? [v]
  (or (map? v) (vector? v)))

(defn ->v-str [v]
  (cond
   (number? v) v
   (keyword? v) v
   (string? v) (pr-str v)
   (boolean? v) v
   (expand? v) "[SKY]->[]"
   :else (str "[GRAY]" (str v) "[]")))

(defn ->labelstr [k v]
  (str "[LIGHT_GRAY]:"
       (when-let [ns (namespace k)] (str ns "/")) "[WHITE]" (name k)
       ": [GOLD]" (str (->v-str v))))

(defn add-map-nodes! [ctx parent-node m level]
  ;(println "Level: " level " - go deeper? " (< level 4))
  (when (< level 4)
    (doseq [[k v] (into (sorted-map) m)]
      ;(println "add-map-nodes! k " k)
      (try
       (let [node (->node (->label ctx (->labelstr k v)))]
         (.add parent-node node)
         (when (map? v)
           (add-map-nodes! ctx node v (inc level)))
         (when (vector? v)
           (doseq [element v
                   :let [el-node (->node (->label ctx (str (->v-str element))))]]
             (.add node el-node))))
       (catch Throwable t
         (println "Error for" k)
         (.add parent-node (->node (->label ctx (str "[RED] "k " - " t)))))))))

(defn- ->prop-tree [ctx prop]
  (let [tree (->tree)]
    (add-map-nodes! ctx tree prop 0)
    tree))

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (->table ctx {:rows rows
                            :cell-defaults {:pad 1}
                            :pack? true})
        scroll-pane (->scroll-pane ctx table)]
    {:actor scroll-pane
     :height (min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))}))

(comment
 (let [ctx @gdl.app/current-context
       entity (cdq.api.context/get-entity ctx 2)
       ]

   (clojure.pprint/pprint
    (sort
     (keys @entity)))

   ))

(defn- get-namespaces []
  (filter #(#{"data" "cdq" "mapgen" "utils" "gdl"}
            (first (str/split (name (ns-name %)) #"\.")))
          (all-ns)))

(defn- get-non-fn-vars [nmspace]
  (for [[sym avar] (ns-interns nmspace)
        :let [value @avar]
        :when (not (or (fn? value)
                       (instance? clojure.lang.MultiFn value)
                       #_(:method-map value) ; breaks for stage Ilookup
                       ))]
    avar))

(comment
 (gdl.libgdx.dev/restart!)

 (spit "app-values-tree.clj"
       (with-out-str
        (pprint
         (for [nmspace (sort-by (comp name ns-name)
                                (get-namespaces))
               :let [value-vars (get-non-fn-vars nmspace)]
               :when (seq value-vars)]
           [(ns-name nmspace) (map (comp symbol name symbol) value-vars)]))))


 (require '[cdq.api.context :refer [get-entity]])
 (let [entity* @(get-entity @gdl.app/current-context 49)]
   (:mana entity*)
   )

 )

(comment
 ; TODO make tree of namespace parts ! not so many elements
 ; and all components namespaced names
 ; and make for entities/cells too !
 ; and cells no atoms! grid! I change multiple at once ...
 ; maybe only add elements on click -> somehow glyphlayout breaks AFTER this returns successfully
 (let [ctx @gdl.app/current-context

       position (ctx/world-mouse-position ctx)
       cell (get (cdq.api.context/world-grid ctx) (mapv int position))

       ;tree-map @cell
       ;tree-map @@(:context/mouseover-entity ctx)
       tree-map ctx

       ]
   (add-to-stage! ctx (->window ctx {:title "Context Overview"
                                     :close-button? true
                                     :close-on-escape? true
                                     :center? true
                                     :rows [[(->scroll-pane-cell ctx [[(->prop-tree ctx (into (sorted-map) tree-map))]])]]
                                     :pack? true}))))
