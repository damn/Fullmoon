(ns core.entity
  (:require [clojure.gdx :refer :all]
            [clojure.ctx :refer :all]
            [malli.core :as m]
            [clj-commons.pretty.repl :refer [pretty-pst]])
  (:load "entity/base"
         "entity/image"
         "entity/animation"
         "entity/movement"
         "entity/clickable"
         "entity/delete_after_duration"
         "entity/destroy_audiovisual"
         "entity/projectile"
         "entity/skills"
         ))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defn- calculate-mouseover-entity [ctx]
  (let [player-entity* (player-entity* ctx)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (point->entities ctx
                                           (world-mouse-position ctx))))]
    (->> render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? ctx player-entity* %))
         first
         :entity/id)))

(def ^:private ctx-mouseover-entity :context/mouseover-entity)

(defn mouseover-entity* [ctx]
  (when-let [entity (ctx-mouseover-entity ctx)]
    @entity))

(defn update-mouseover-entity [ctx]
  (let [entity (if (mouse-on-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    [(when-let [old-entity (ctx-mouseover-entity ctx)]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx ctx-mouseover-entity entity))]))

(defn enemy-faction [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friendly-faction [{:keys [entity/faction]}]
  faction)

(defcomponent :entity/faction
  {:let faction
   :data [:enum [:good :evil]]}
  (info-text [_ _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))

(defcomponent :entity/line-render
  {:let {:keys [thick? end color]}}
  (render [_ entity* g _ctx]
    (let [position (:position entity*)]
      (if thick?
        (with-shape-line-width g 4 #(draw-line g position end color))
        (draw-line g position end color)))))

(defcomponent :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}] _ctx]
    [[:e/create
      start
      effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))

(defcomponent :entity/mouseover?
  (render-below [_ {:keys [entity/faction] :as entity*} g ctx]
    (let [player-entity* (player-entity* ctx)]
      (with-shape-line-width g 3
        #(draw-ellipse g
                       (:position entity*)
                       (:half-width entity*)
                       (:half-height entity*)
                       (cond (= faction (enemy-faction player-entity*))
                             enemy-color
                             (= faction (friendly-faction player-entity*))
                             friendly-color
                             :else
                             neutral-color))))))

(defcomponent :entity/temp-modifier
  {:let {:keys [counter modifiers]}}
  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Spiderweb - remaining: " (readable-number (finished-ratio ctx counter)) "/1[]"))

  (tick [[k _] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]
       [:tx/reverse-modifiers eid modifiers]]))

  (render-above [_ entity* g _ctx]
    (draw-filled-circle g (:position entity*) 0.5 [0.5 0.5 0.5 0.4])))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter ctx delay-seconds)
        :faction faction}}]]))

(defcomponent :entity/string-effect
  (tick [[k {:keys [counter]}] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]]))

  (render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (draw-text g
                 {:text text
                  :x x
                  :y (+ y (:half-height entity*) (pixels->world-units g hpbar-height-px))
                  :scale 2
                  :up? true}))))

(defcomponent :tx/add-text-effect
  (do! [[_ entity text] ctx]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(reset ctx %)))
        {:text text
         :counter (->counter ctx 0.4)})]]))

(defsystem enter "FIXME" [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  "FIXME" [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem player-enter "FIXME" [_])
(defmethod player-enter :default [_])

(defsystem pause-game? "FIXME" [_])
(defmethod pause-game? :default [_])

(defsystem manual-tick "FIXME" [_ ctx])
(defmethod manual-tick :default [_ ctx])

(defsystem clicked-inventory-cell "FIXME" [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defsystem clicked-skillmenu-skill "FIXME" [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])
