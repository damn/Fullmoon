(ns dev
  (:require [clojure.pprint :refer :all]
            [clojure.string :as str]
            utils.ns
            [core.context :as ctx :refer :all]
            [core.entity :as entity]
            [core.scene2d.actor :as actor]
            app))
(comment
 (defn- component-systems [component-k]
   (for [[sys-name sys-var] defsystems
         [k method] (methods @sys-var)
         :when (= k component-k)]
     sys-name))

 (spit "components.txt"
       (with-out-str
        (clojure.pprint/pprint (group-by namespace
                                         (sort (keys attributes))))))

 (ancestors :op/val-inc)

 (spit "components.md"
       (binding [*print-level* nil]
         (with-out-str
          (doseq [[nmsp components] (sort-by first
                                             (group-by namespace
                                                       (sort (keys attributes))))]
            (println "\n#" nmsp)
            (doseq [k components]
              (println "*" k
                       (if-let [ancestrs (ancestors k)]
                         (str "-> "(clojure.string/join "," ancestrs))
                         "")
                       (let [attr-map (get attributes k)]
                         (if (seq attr-map)
                           (pr-str (:core.component/fn-params attr-map))
                           #_(binding [*print-level* nil]
                               (with-out-str
                                (clojure.pprint/pprint attr-map)))
                           "")))
              #_(doseq [system-name (component-systems k)]
                  (println "  * " system-name)))))))

 ; & add all tx's ?

 ; -> only components who have a system ???
 ; -> not skill/cost or something ...
 ; and each system just add docstring
 ; and component schema
 ; then expandable and upload to wiki

 ; https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
 ; https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/organizing-information-with-collapsed-sections

 ; -> and after each 'build' I can have a bash script which uploads the components go github
 )

(comment

 (import 'com.badlogic.gdx.graphics.g2d.TextureAtlas)
 (gdx.app/post-runnable
  (fn []
    (let [atlas (TextureAtlas. "images/creature_animations.atlas")
          region (.findRegion atlas "barbarian-f-2")]
      (println region))

    ; but commenting out render-entities / render-tiled-map
    ; didnt change FPS at all (120)
    ; thats strange I thought texture swaps take the most time

    )))

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

; TODO expand only on click ... save bandwidth ....
; crash only on expanding big one ...

(defn- add-elements! [ctx node elements]
  (doseq [element elements
          :let [el-node (->node (->label ctx (str (->v-str element))))]]
    (.add node el-node)))

(require '[core.scene2d.group :as group])
(require '[gdx.scene2d.stage :as stage])

(declare add-map-nodes!)

(defn- children->str-map [children]
  (zipmap (map str children)
          children
          ))

(defn- ->nested-nodes [ctx node level v]
  (when (map? v)
    (add-map-nodes! ctx node v (inc level)))

  (when (and (vector? v) (>= (count v) 3))
    (add-elements! ctx node v))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Stage v)
    (add-map-nodes! ctx node (children->str-map (group/children (stage/root v))) level))

  (when (instance? com.badlogic.gdx.scenes.scene2d.Group v)
    (add-map-nodes! ctx node (children->str-map (group/children v)) level))
  )

(comment
 (let [vis-image (first (group/children (stage/root (ctx/get-stage @app/state))))]
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
       (let [node (->node (->label ctx (->labelstr k v)))]
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
 (let [ctx @app/state
       entity (core.context/get-entity ctx 2)
       ]

   (clojure.pprint/pprint
    (sort
     (keys @entity)))

   ))


(comment
 (clojure.pprint/pprint (get-namespaces-by-exclude)))

(defn- protocol? [value]
  (and (instance? clojure.lang.PersistentArrayMap value)
       (:on value)))

(defn- get-non-fn-vars [nmspace]
  (utils.ns/get-vars nmspace
                     (fn [avar]
                       (let [value @avar]
                         (not (or (fn? value)
                                  (instance? clojure.lang.MultiFn value)
                                  #_(:method-map value) ; breaks for stage Ilookup
                                  (protocol? value)
                                  (instance? java.lang.Class value) ;anonymous class (proxy)
                                  ))))))

(comment

 (spit "app-values-tree.clj"
       (with-out-str
        (pprint
         (for [nmspace (sort-by (comp name ns-name)
                                (utils.ns/get-namespaces
                                 (fn [first-ns-part-str]
                                   (not (#{"clojure"
                                           "nrepl"
                                           "malli"
                                           "user"
                                           "borkdude"
                                           "clj-commons"
                                           "dorothy"
                                           "reduce-fsm"}
                                         first-ns-part-str)))))
               :let [value-vars (get-non-fn-vars nmspace)]
               :when (seq value-vars)]
           [(ns-name nmspace) (map (comp symbol name symbol) value-vars)]))))


 (require '[core.context :refer [get-entity]])
 (let [entity* @(get-entity @app/state 49)]
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


(defn show-tree-view! [obj]
  (let [ctx @app/state
        object (case obj
                 :ctx ctx
                 :entity (ctx/mouseover-entity* ctx)
                 :tile @(get (core.context/world-grid ctx) (mapv int (ctx/world-mouse-position ctx))))]
    (add-to-stage! ctx (->window ctx {:title "Tree View"
                                      :close-button? true
                                      :close-on-escape? true
                                      :center? true
                                      :rows [[(->scroll-pane-cell ctx [[(->prop-tree ctx (into (sorted-map) object))]])]]
                                      :pack? true}))))

(comment

 ;; learn skill for player


 )

(defn- do-on-ctx! [tx-fn]
  (swap! app/state ctx/do! [(tx-fn @app/state)]))

(defn learn-skill! [skill-id]
  (do-on-ctx!  (fn [ctx]
                 [:tx/add-skill
                  (:entity/id (ctx/player-entity* ctx))
                  (ctx/get-property ctx skill-id)])))

(defn create-item! [item-id]
  (do-on-ctx!  (fn [ctx]
                 [:tx.entity/item
                  (:position (ctx/player-entity* ctx))
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
 ; so can see errors ... don't see proper stacktrace if evaluating just in editor

 ; TODO I want to maintain the test-map with test-items & test-skills
 ; - maybe put in the map-properties player items/skills ....
 (gdx.app/post-runnable (fn []
                          (do
                           (learn-skill! :skills/projectile)
                           (learn-skill! :skills/spawn)
                           (learn-skill! :skills/meditation)
                           (learn-skill! :skills/melee-attack)
                           (learn-skill! :skills/death-ray)
                           (learn-skill! :skills/convert)
                           (learn-skill! :skills/blood-curse)
                           (learn-skill! :skills/slow)
                           )))


 (create-item! :items/blood-glove)

 (gdx.app/post-runnable (fn [] (show-tree-view! :ctx)))
 (show-tree-view! :entity)
 (show-tree-view! :tile)

 (let [ctx @app/state
       pl (ctx/player-entity* ctx)
       ]
   (-> pl
       :entity/stats
       :stats/modifiers
       :modifier/mana))

 (binding [*print-level* nil]
   (clojure.repl/pst *e))

 )
