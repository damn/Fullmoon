(ns clojure.gdx
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [stage-get stage-add!]]
            [core.component :refer [defsystem defc do! effect!] :as component]
            [core.data :as data]
            [core.db :as db]
            [core.property :as property]
            [data.grid2d :as g2d]
            [malli.core :as m]
            [utils.core :refer [readable-number]]
            [world.entity :as entity]
            [world.entity.faction :as faction]
            [world.entity.state :as entity-state]
            [world.grid :as grid :refer [world-grid]]
            [world.player :refer [world-player]]
            [world.time :refer [->counter stopped? world-delta finished-ratio]]))

(defprotocol Stats
  (entity-stat [_ stat] "Calculating value of the stat w. modifiers"))

(defc :tx.entity.stats/pay-mana-cost
  (do! [[_ entity cost]]
    (let [mana-val ((entity-stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities world-grid)
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defc :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (entity/tick [_ eid]
    (when (stopped? counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defc :tx/shout
  (do! [[_ position faction delay-seconds]]
    [[:e/create
      position
      entity/effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter delay-seconds)
        :faction faction}}]]))

(def hpbar-height-px 5)

(defc :entity/string-effect
  (entity/tick [[k {:keys [counter]}] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]]))

  (entity/render-above [[_ {:keys [text]}] entity*]
    (let [[x y] (:position entity*)]
      (g/draw-text {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defc :tx/add-text-effect
  (do! [[_ entity text]]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter world.time/reset))
        {:text text
         :counter (->counter 0.4)})]]))

(g/def-markup-color "ITEM_GOLD" [0.84 0.8 0.52])

(defc :property/pretty-name
  {:data :string
   :let value}
  (component/info [_]
    (str "[ITEM_GOLD]"value"[]")))

(defsystem pause-game?)
(defmethod pause-game? :default [_])

(defsystem manual-tick)
(defmethod manual-tick :default [_])

(defc :tx/cursor
  (do! [[_ cursor-key]]
    (g/set-cursor! cursor-key)
    nil))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [{:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get))))
  (stage-add! (ui/window {:title title
                          :rows [[(ui/label text)]
                                 [(ui/text-button button-text
                                                  (fn []
                                                    (a/remove! (::modal (stage-get)))
                                                    (on-click)))]]
                          :id ::modal
                          :modal? true
                          :center-position [(/ (g/gui-viewport-width) 2)
                                            (* (g/gui-viewport-height) (/ 3 4))]
                          :pack? true})))

(defc :tx/player-modal
  (do! [[_ params]]
    (show-player-modal! params)
    nil))
