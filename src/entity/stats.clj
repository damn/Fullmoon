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
; * grep entity/stats and move all operations/accessors here through entity API
; * implement component texts
; * replace modifiers @ properties.edn
; * schema for allowed-operations/values/bounds?
; * ( editor widgets )

; LATER TODO
; [:tx.entity/assoc-in id [:entity/stats :stats/mana] (apply-val (entity/mana entity*) #(- % cost))]
; rule: don't know about internal state of components
; nowhere call :entity/body
; nowhere :entity/stats keyword directly
; same with contexts's etc.
; then can later make records & superfast.


(comment

 ; TODO test the new transaction functions
 (let [entity* {:entity/stats {:stats/modifiers {:stats/hp {[:max :inc] [10]}}}}
       [stat operation value] [:stats/hp [:max :inc] 2]]
   (update-in entity* [:entity/stats :stats/modifiers stat operation] conj value))

 (let [entity* #:entity{:stats #:stats{:modifiers #:stats{:hp {[:max :inc] [10 2]}}}}
       [stat operation value] [:stats/hp [:max :inc] 10]]
   (update-in entity* [:entity/stats :stats/modifiers stat operation]
              (fn [values] (remove #{value} values))))

 )

(defn- check-plus-symbol [n]
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

(defcomponent :stats/hp data/pos-int-attr)

; required @ npc state, for cost, check if nil
(defcomponent :stats/mana data/nat-int-attr)

; for adding speed multiplier modifier -> need to take max-speed into account!
(defcomponent :stats/movement-speed data/pos-attr)

; TODO for each stat - data-type - define which operations are available
; check stats @ properties load
; apply modifiers which are available
; e.g. + mana/hp max has to be an integer....

(defmulti apply-modifiers (fn [stat _stats] stat))

(defmethod apply-modifiers :stat/plain-number [stat stats]
  (let [base-value (stat stats)
        modifiers (stat (:stats/modifiers stats))
        inc-modifiers  (reduce + (:inc  modifiers))
        mult-modifiers (reduce + 1 (:mult modifiers))]
    (-> base-value
        (+ inc-modifiers)
        (* mult-modifiers))))

(defmethod apply-modifiers :stat/val-max [stat stats]
  (stat stats)
  #_(let [base-value (stat stats)
        modifiers (stat (:stats/modifiers stats))
        inc-modifiers  (reduce + (:inc  modifiers))
        mult-modifiers (reduce + 1 (:mult modifiers))]
    (-> base-value
        (+ inc-modifiers)
        (* mult-modifiers))))

(derive :stats/movement-speed :stat/plain-number)

(derive :stats/hp   :stat/val-max)
(derive :stats/mana :stat/val-max)

; TODO hp/mana has [:max :inc] and [:max :mult ]
; check bounds after apply/allowed value range/etc.

(defn- effective-value [entity* stat]
  (apply-modifiers stat (:entity/stats entity*)))

; TODO every stat here ....
(extend-type api.entity.Entity
  entity/Stats
  (hp             [entity*] (effective-value entity* :stats/hp))
  (mana           [entity*] (effective-value entity* :stats/mana))
  (movement-speed [entity*] (effective-value entity* :stats/movement-speed)))

(comment
 (require '[api.context :as ctx])

 (let [ctx @app.state/current-context
       e* (ctx/player-entity* ctx)]
   (:entity/stats e*)
   (entity/movement-speed e*))

 ; GREP entity/stats
 ; and replace data access by stat getters. (damage)
 )

(defcomponent :stats/strength data/nat-int-attr)

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)]
  (defcomponent :stats/cast-speed   skill-speed-stat)
  (defcomponent :stats/attack-speed skill-speed-stat))
; TODO this is already a mulitplier ...  so we dont have mult stats??

(defcomponent :stats/armor-save   {:widget :text-field :schema number?})
(defcomponent :stats/armor-pierce {:widget :text-field :schema number?})

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

(defcomponent :entity/stats
  (data/components-attribute :stats)
  #_(assoc (data/map-attribute :stats/movement-speed
                               :stats/strength
                               :stats/cast-speed
                               :stats/attack-speed
                               :stats/armor-save
                               :stats/armor-pierce)
      ; TODO also DRY @ modifier.all is default value 1 too...
      ; TODO default value missing... empty when created
      :default-value {:stats/movement-speed 1
                      :stats/strength 1
                      :stats/cast-speed 1
                      :stats/attack-speed 1
                      :stats/armor-save 0
                      :stats/armor-pierce 0}
      )

  (entity/create-component [[_ stats] _components _ctx]
    (-> stats
        (update :stats/hp (fn [hp] [hp hp]))
        (update :stats/mana (fn [mana] [mana mana])) ))

  (entity/render-info [_
                       {{:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?] :as entity*}
                       g
                       _ctx]
    (let [ratio (val-max-ratio (entity/hp entity*))
          [x y] (entity/position entity*)]
      (when (or (< ratio 1) mouseover?)
        (let [x (- x half-width)
              y (+ y half-height)
              height (g/pixels->world-units g hpbar-height-px) ; pre-calculate it maybe somehow, but will put too much stuff in properties?
              border (g/pixels->world-units g borders-px)] ; => can actually still use global state? idk
          (g/draw-filled-rectangle g x y width height color/black)
          (g/draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio)))))))

; New problem:
; creature have all stats now
; and missing keys

; => make optional or migrate to add all stats eveverywhere ?



(defn- check-damage-modifier-value [[source-or-target
                                     application-type
                                     value-delta]]
  (and (#{:damage/deal :damage/receive} source-or-target)
       (let [[val-or-max inc-or-mult] application-type]
         (and (#{:val :max} val-or-max)
              (#{:inc :mult} inc-or-mult)))))

(defn- default-value [application-type] ; TODO here too !
  (let [[val-or-max inc-or-mult] application-type]
    (case inc-or-mult
      :inc 0
      :mult 1)))

(defn- damage-modifier-text [[source-or-target
                              [val-or-max inc-or-mult]
                              value-delta]]
  (str/join " "
            [(case val-or-max
               :val "Minimum"
               :max "Maximum")
             (case source-or-target
               :damage/deal "dealt"
               :damage/receive "received")
             ; TODO not handling negative values yet (do I need that ?)
             (case inc-or-mult
               :inc "+"
               :mult "+")
             (case inc-or-mult
               :inc value-delta
               :mult (str (int (* value-delta 100)) "%"))]))

(defcomponent :modifier/damage {:widget :text-field :schema :some}  ; TODO no schema
  (modifier/text [[_ value]]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (damage-modifier-text value))
  (modifier/keys [_] [:entity/stats :stats/damage])
  (modifier/apply [[_ value] stat]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (update-in stat (drop-last value) #(+ (or % (default-value (get value 1)))
                                          (last value))))
  (modifier/reverse [[_ value] stat]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (update-in stat (drop-last value) - (last value))))
