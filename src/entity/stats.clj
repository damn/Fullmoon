(ns entity.stats
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [data.val-max :refer [val-max-ratio]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            api.context
            [api.entity :as entity]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.tx :refer [transact!]]
            [context.ui.config :refer (hpbar-height-px)]))

; TODO
; * effect/target-entity - valid-params? broken
; * properties.item - :item/modifier no schema
; * default values
; * bounds (action-speed not <=0 , not value '-1' e.g.)/schema/values/allowed operations
; * editable in editor ? or first keep @ properties.edn ?
; * stats/modifiers in editor / for example max dmg reduced by 5 at stone golem
; * :inc :mult -> use namespaced keyword
; * we only work on max so could just use inc/mult @ val-max-modifiers hp/mana
; * take max-movement-speed into account @ :stats/movement-speed

#_(defn- check-plus-symbol [n]
  (case (math/signum n)
    (0.0 1.0) "+"
    -1.0 ""))

#_(defn- actions-speed-percent [v]
  (str (check-plus-symbol v) (int (* 100 v))))

(extend-type api.context.Context
  api.context/Modifier
  (modifier-text [_ modifier] ; only called at properties.item
    (binding [*print-level* nil]
      (with-out-str (clojure.pprint/pprint modifier)))
    #_(->> (for [component modifier] (modifier/text component))
         (str/join "\n"))))

(defn- update-modifiers [entity [stat operation] f]
  [:tx.entity/update-in entity [:entity/stats :stats/modifiers stat operation] f])

(defmethod transact! :tx/apply-modifier [[_ entity modifier] ctx]
  (for [{value 2 :as component} modifier]
    (update-modifiers entity component #(conj % value))))

(defmethod transact! :tx/reverse-modifier [[_ entity modifier] ctx]
  (for [{value 2 :as component} modifier]
    (update-modifiers entity component (fn [values]
                                         {:post [(= (count %) (dec (count values)))]}
                                         (remove #{value} values)))))

(defmulti apply-modifiers (fn [stat _stats] stat))

(defmethod apply-modifiers :stat/plain-number [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers))
        mult-modifiers (reduce + 1 (:mult modifiers))]
    (-> (stat stats)
        (+ inc-modifiers)
        (* mult-modifiers))))

(defmethod apply-modifiers :stat/only-inc [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers))]
    (-> (stat stats)
        (+ inc-modifiers))))

(defmethod apply-modifiers :stat/val-max [stat stats]
  (let [base-value (stat stats)
        modifiers (stat (:stats/modifiers stats))]
    (data.val-max/apply-val-max-modifiers base-value modifiers)))

(defn- effective-value [entity* stat]
  (apply-modifiers stat (:entity/stats entity*)))

(defcomponent :stats/hp data/pos-int-attr)
(derive :stats/hp :stat/val-max)

(defcomponent :stats/mana data/nat-int-attr)
(derive :stats/mana :stat/val-max)

(defcomponent :stats/movement-speed data/pos-attr)
(derive :stats/movement-speed :stat/plain-number)

(defcomponent :stats/strength data/nat-int-attr)
(derive :stats/strength :stat/only-inc)

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)]
  (defcomponent :stats/cast-speed   skill-speed-stat)
  (defcomponent :stats/attack-speed skill-speed-stat))
(derive :stats/cast-speed :stat/only-inc)
(derive :stats/attack-speed :stat/only-inc)

(defcomponent :stats/armor-save {:widget :text-field :schema number?})
(derive :stats/armor-save :stat/only-inc)

(defcomponent :stats/armor-pierce {:widget :text-field :schema number?})
(derive :stats/armor-pierce :stat/only-inc)

(defcomponent :stats.damage/deal {})
(defcomponent :stats.damage/receive {})

(extend-type api.entity.Entity
  entity/Stats
  (stat [entity* stat]
    (when (stat (:entity/stats entity*))
      (effective-value entity* stat))))

(def ^:private hpbar-colors
  {:green     [0 0.8 0]
   :darkgreen [0 0.5 0]
   :yellow    [0.5 0.5 0]
   :red       [0.5 0 0]})

(defn- hpbar-color [ratio]
  (let [ratio (float ratio)
        color (cond
                (> ratio 0.75) :green
                (> ratio 0.5)  :darkgreen
                (> ratio 0.25) :yellow
                :else          :red)]
    (color hpbar-colors)))

(def ^:private borders-px 1)

(defcomponent :entity/stats (data/components-attribute :stats)
  (entity/create-component [[_ stats] _components _ctx]
    (-> stats
        (update :stats/hp (fn [hp] [hp hp]))
        (update :stats/mana (fn [mana] [mana mana])) ))

  (entity/render-info [_
                       {{:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?] :as entity*}
                       g
                       _ctx]
    (let [ratio (val-max-ratio (entity/stat entity* :stats/hp))
          [x y] (entity/position entity*)]
      (when (or (< ratio 1) mouseover?)
        (let [x (- x half-width)
              y (+ y half-height)
              height (g/pixels->world-units g hpbar-height-px)
              border (g/pixels->world-units g borders-px)]
          (g/draw-filled-rectangle g x y width height color/black)
          (g/draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio)))))))
