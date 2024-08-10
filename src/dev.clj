(ns dev
  (:require [clojure.pprint :refer :all]
            [clojure.string :as str]
            [api.context :as ctx :refer :all]
            [api.entity :as entity]
            [api.scene2d.actor :as actor]))

(comment
 (defn- all-text-colors []
   (let [colors (seq (.keys (com.badlogic.gdx.graphics.Colors/getColors)))]
     (str/join "\n"
               (for [colors (partition-all 4 colors)]
                 (str/join " , " (map #(str "[" % "]" %) colors)))))))

(import 'com.badlogic.gdx.scenes.scene2d.ui.Tree$Node)
(import 'com.kotcrab.vis.ui.widget.VisTree)

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
   (and (vector? v) (< (count v) 3)) v
   (vector? v) (str "Vector "(count v))
   :else (str "[GRAY]" (str v) "[]")))

(defn ->labelstr [k v]
  (str "[LIGHT_GRAY]:"
       (when-let [ns (namespace k)] (str ns "/")) "[WHITE]" (name k)
       ": [GOLD]" (str (->v-str v))))

; TODO expand only on click ... save bandwidth ....
; crash only on expanding big one ...

(declare add-map-nodes!)

(defn- ->nested-nodes [ctx node level v]
  (when (map? v)
    (add-map-nodes! ctx node v (inc level)))
  (when (and (vector? v) (>= (count v) 3))
    (doseq [element v
            :let [el-node (->node (->label ctx (str (->v-str element))))]]
      (.add node el-node))))

(defn add-map-nodes! [ctx parent-node m level]
  ;(println "Level: " level " - go deeper? " (< level 4))
  (when (< level 4)
    (doseq [[k v] (into (sorted-map) m)]
      ;(println "add-map-nodes! k " k)
      (try
       (let [node (->node (->label ctx (->labelstr k v)))]
         (.add parent-node node)
         (when (instance? clojure.lang.Atom v)
           (->nested-nodes ctx node level @v))
         (->nested-nodes ctx node level v))
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
     :width (/ (ctx/gui-viewport-width ctx) 2)
     :height
     (- (ctx/gui-viewport-height ctx) 50)
     #_(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))}))

(comment
 (let [ctx @app.state/current-context
       entity (api.context/get-entity ctx 2)
       ]

   (clojure.pprint/pprint
    (sort
     (keys @entity)))

   ))

(defn- get-namespaces []
  (filter #(#{"api"
              "app"
              "context"
              "core"
              "data"
              "effect"
              "entity"
              "mapgen"
              "math"
              "modifier"
              "properties"
              "property"
              "screens"
              "tx"
              "utils"
              "world"
              "dev"
              }
            (first (str/split (name (ns-name %)) #"\.")))
          (all-ns)))

(defn- get-non-fn-vars [nmspace]
  (for [[sym avar] (ns-interns nmspace)
        :let [value @avar]
        :when
        ;(:debug (meta avar))

        (not (or (fn? value)
                 (instance? clojure.lang.MultiFn value)
                 #_(:method-map value) ; breaks for stage Ilookup
                 ))]
    avar))

(comment

 (spit "app-values-tree.clj"
       (with-out-str
        (pprint
         (for [nmspace (sort-by (comp name ns-name)
                                (get-namespaces))
               :let [value-vars (get-non-fn-vars nmspace)]
               :when (seq value-vars)]
           [(ns-name nmspace) (map (comp symbol name symbol) value-vars)]))))


 (require '[api.context :refer [get-entity]])
 (let [entity* @(get-entity @app.state/current-context 49)]
   (:mana entity*)
   )

 )

; TODO make a menu at top .... with debug options etc...

(comment
 ; TODO make tree of namespace parts ! not so many elements
 ; and all components namespaced names
 ; and make for entities/cells too !
 ; and cells no atoms! grid! I change multiple at once ...
 ; maybe only add elements on click -> somehow glyphlayout breaks AFTER this returns successfully
 )
(defn show-context! []
  (let [ctx @app.state/current-context

        position (ctx/world-mouse-position ctx)
        cell (get (api.context/world-grid ctx) (mapv int position))

        ;tree-map @cell
        tree-map (ctx/mouseover-entity* ctx)
        ;tree-map ctx

        ;tree-map (sort-by first core.component/attributes)
        ;tree-map (sort-by first (methods api.tx/transact!))

        ]
    (add-to-stage! ctx (->window ctx {:title "Context Overview"
                                      :close-button? true
                                      :close-on-escape? true
                                      :center? true
                                      :rows [[(->scroll-pane-cell ctx [[(->prop-tree ctx (into (sorted-map) tree-map))]])]]
                                      :pack? true}))))

(comment

 ;; learn skill for player


 )

(defn- do-on-ctx! [tx-fn]
  (swap! app.state/current-context ctx/transact-all! [(tx-fn @app.state/current-context)]))

(defn learn-skill! [skill-id]
  (do-on-ctx!  (fn [ctx]
                 [:tx/add-skill
                  (:entity/id (ctx/player-entity* ctx))
                  (ctx/get-property ctx skill-id)])))

(defn create-item! [item-id]
  (do-on-ctx!  (fn [ctx]
                 [:tx.entity/item
                  (entity/position (ctx/player-entity* ctx))
                  (ctx/get-property ctx item-id)])))



(comment

 (require '[clojure.string :as str])
 (clojure.pprint/pprint
  (sort
   (filter #(and (namespace %)
                 (str/starts-with? (namespace %) "context"))
           (keys component/attributes))))

 )


(comment
 (import 'com.kotcrab.vis.ui.widget.MenuBar)
 (import 'com.kotcrab.vis.ui.widget.Menu)
 (import 'com.kotcrab.vis.ui.widget.MenuItem)

 (defn- ->menu-bar []
   (let [menu-bar (MenuBar.)
         app-menu (Menu. "App")]
     (.addItem app-menu (MenuItem. "New app"))
     (.addItem app-menu (MenuItem. "Load app"))
     (.addMenu menu-bar app-menu)
     (.addMenu menu-bar (Menu. "Properties"))
     (def mybar menu-bar)
     menu-bar))

 (defn- ->menu [ctx]
   (ctx/->table ctx {:rows [[{:actor (.getTable (->menu-bar))
                              ;:left? true
                              :expand-x? true
                              :fill-x? true
                              :colspan 1}]
                            [{:actor (ctx/->label ctx "")
                              :expand? true
                              :fill-x? true
                              :fill-y? true}]]
                     :fill-parent? true})))

(comment
 (.postRunnable ; so can see errors ... don't see proper stacktrace if evaluating just in editor
  com.badlogic.gdx.Gdx/app
  (fn []
    (do
     (learn-skill! :skills/projectile)
     (learn-skill! :skills/spawn)
     (learn-skill! :skills/meditation)
     (learn-skill! :skills/melee-attack))))


 (create-item! :items/blood-glove)

 (show-context!)

 )
