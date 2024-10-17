(ns ^:no-doc dev.experiment
  (:require [component.core :as component]
            [component.db :as db]
            [component.tx :as tx]
            [gdx.app :refer [post-runnable!]]))

(comment

 (print-txs "txs.md")
 (print-components "components.md")

 ; * Test
 ; * if z-order/effect renders behind wall
 ; * => graphics txs?
 (post-tx! [:tx/line-render {:start [68 38]
                             :end [70 30]
                             :color [1 1 1]
                             :duration 2}])

 (do ; this only works in game screen otherwise action-bar uses wrong stage !!
     ; remove anyway other screens?! optionsmenu not needed -> menubar in dev mode
  (learn-skill! :skills/projectile)
  (learn-skill! :skills/spawn)
  (learn-skill! :skills/meditation)
  (learn-skill! :skills/death-ray)
  (learn-skill! :skills/convert)
  (learn-skill! :skills/blood-curse)
  (learn-skill! :skills/slow)
  (learn-skill! :skills/double-fireball))

 ; FIXME
 ; first says inventory is full
 ; ok! beholder doesn't have inventory - player entity needs inventory/...
 ; => tests...
 (create-item! :items/blood-glove)

 (require '[clojure.string :as str])
 (spit "item_tags.txt"
       (with-out-str
        (clojure.pprint/pprint
         (distinct
          (sort
           (mapcat
            (comp #(str/split % #"-")
                  name
                  :property/id)
            (db/all :properties/items)))))))

 )

(comment
 ; start world - small empty test room
 ; 2 creatures - player?
 ; start skill w. applicable needs target (bow)
 ; this command:
 (post-tx! [:e/destroy (entity/get-entity 68)])
 ; check skill has stopped using

 (post-tx! [:tx/creature {:position [35 73]
                          :creature-id :creatures/dragon-red
                          :components {:entity/state [:state/npc :npc-sleeping]
                                       :entity/faction :evil} }])
 )

(defn- post-tx! [tx]
  (post-runnable! (tx/do-all [tx])))

(defn- learn-skill! [skill-id] (post-tx! (fn [] [[:tx/add-skill world/player (db/get skill-id)]])))
(defn- create-item! [item-id]  (post-tx! (fn [] [[:tx/item       (:position @world/player) (db/get item-id)]])))

; https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
; https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/organizing-information-with-collapsed-sections
; -> and after each 'build' I can have a bash script which uploads the components go github

; => component-attributes private
; => move component docs in here
; => I need also an internal documentation ??
; of private fns?!

(defn- print-txs [file]
  (spit file
        (binding [*print-level* nil]
          (with-out-str
           (doseq [[nmsp ks] (sort-by first
                                      (group-by namespace (sort (keys (methods tx/do!)))))]

             (println "\n#" nmsp)
             (doseq [k ks
                     :let [attr-m (component/meta k)]]
               (println (str "* __" k "__ `" (get (:params attr-m) "tx/do!") "`"))
               (when-let [data (:schema attr-m)]
                 (println (str "    * data: `" (pr-str data) "`")))
               (let [ks (descendants k)]
                 (when (seq ks)
                   (println "    * Descendants"))
                 (doseq [k ks]
                   (println "      *" k)
                   (println (str "        * data: `" (pr-str (:schema (component/meta k))) "`"))))))))))

(defn- component-systems [component-k]
   (for [[sys-name sys-var] component/systems
         [k method] (methods @sys-var)
         :when (= k component-k)]
     sys-name))

(defn- print-components* [ks]
  (doseq [k ks]
    (println "*" k
             (if-let [ancestrs (ancestors k)]
               (str "-> "(clojure.string/join "," ancestrs))
               "")
             (let [attr-map (component/meta k)]
               #_(if (seq attr-map)
                   (pr-str (:component.core/fn-params attr-map))
                   (str " `"
                        (binding [*print-level* nil]
                          (with-out-str
                           (clojure.pprint/pprint (dissoc attr-map :params))))
                        "`\n"
                        )
                   "")
               ""))
    #_(doseq [system-name (component-systems k)]
        (println "  * " system-name))))

(defn- spit-out [file ks]
  (spit file
        (binding [*print-level* nil]
          (with-out-str
           (print-components* ks)))))

(defn- print-components [file]
  (spit file
        (binding [*print-level* nil]
          (with-out-str
           (doseq [[nmsp components] (sort-by first
                                              (group-by namespace
                                                        (sort (keys component/meta))))]
             (println "\n#" nmsp)
             (print-components* components)
             )))))
