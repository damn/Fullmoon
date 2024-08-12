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

; TODO check default/if not values available for each stat if queried
; e.g. melee damage wha tif strength not available
; or have to be available ???

; TODO for damage and armor-save could even display a tooltip
; e.g. 6-12 damage base => modified 6-4

; modifiers placable @ creatures&items ? in editor ? or algorithmically ?
; => & entities can put in stats/modifiers already prebuilt modifiers for damage
; e.g. stone golem -10 max damage , +50% armor-save
; defcomponent :stats/modifier (s) ->

; TODO
; * grep entity/stats and move all operations/accessors here through entity API
; e.g. mana or something uses entity/mana then assoce's minus will mess up if mixing with val
; * replace modifiers @ properties.edn
; * schema for allowed-operations/values/bounds?
; * test the new transaction functions, not this (leads me to the interface segregation principle to test easily?)
; * ( editor widgets )
; * default values armor-save/strength/etc. ? move into component or function (defensive coding still ?)

;; To Readable Text

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

;; Applying/Reversing modifiers

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

; Application Functions

; TODO hp/mana has [:max :inc] and [:max :mult ]
; check bounds after apply/allowed value range/etc.

(defmulti apply-modifiers (fn [stat _stats] stat))

(defmethod apply-modifiers :stat/plain-number [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers)) ; TODO use :operation/inc / :operation/mult or :op/..
        mult-modifiers (reduce + 1 (:mult modifiers))]
    (-> (stat stats)
        (+ inc-modifiers)
        (* mult-modifiers))))

(defmethod apply-modifiers :stat/only-inc [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers))]
    (-> (stat stats)
        (+ inc-modifiers))))

; TODO use apply-val-max-modifiers
; and operations only [:max :inc] or [:max :mult]
; so there is two things:
; * plain-number or val-max data type
; * allowed operations
(defmethod apply-modifiers :stat/val-max [stat stats]
  (let [base-value (stat stats)
        modifiers (stat (:stats/modifiers stats))]
    (data.val-max/apply-val-max-modifiers base-value modifiers)))

(defn- effective-value [entity* stat]
  ; TODO no armor-save -> NPE
  ; default-value give here ? or obligatory in all stats have to be available
  ; => assert then & @ properties schema obligatory
  (apply-modifiers stat (:entity/stats entity*)))

; TODO for hp/mana - val-max
; we return the actual value of the stat
; and change it on item

;; Stat Definitions

(defcomponent :stats/hp data/pos-int-attr) ; this data is creature/hp and not the schema of the stat itself ...
(derive :stats/hp :stat/val-max) ; pass data type as keyword to defcomponent / defattribute ...

(defcomponent :stats/mana data/nat-int-attr) ; required @ npc state, for cost, check if nil
(derive :stats/mana :stat/val-max)

(defcomponent :stats/movement-speed data/pos-attr) ; for adding speed multiplier modifier -> need to take max-speed into account!
(derive :stats/movement-speed :stat/plain-number)

(defcomponent :stats/strength data/nat-int-attr)
; TODO this stat applies integer to the result ?
; => only inc modifiers
(derive :stats/strength :stat/only-inc)

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)]
  (defcomponent :stats/cast-speed   skill-speed-stat)
  (defcomponent :stats/attack-speed skill-speed-stat))
; TODO this is already a mulitplier ...  so we dont have mult stats??
; only mult ? = as it is multi then only inc ?
(derive :stats/cast-speed :stat/only-inc)
(derive :stats/attack-speed :stat/only-inc)
; TODO added '-1' => value became 0 -> divide by zero !

(defcomponent :stats/armor-save {:widget :text-field :schema number?})
(derive :stats/armor-save :stat/only-inc)

(defcomponent :stats/armor-pierce {:widget :text-field :schema number?})
(derive :stats/armor-pierce :stat/only-inc)

(defcomponent :stats.damage/deal {})
(defcomponent :stats.damage/receive {})
; TODO fix @ damage

(extend-type api.entity.Entity
  entity/Stats
  ; TODO just 1 function for getting stat ??
  ; (entity/stat entity* :stats/hp) ?
  (->stat [entity* stat] (effective-value entity* stat))
  (hp             [entity*] (effective-value entity* :stats/hp))
  (mana           [entity*] (effective-value entity* :stats/mana))
  (movement-speed [entity*] (effective-value entity* :stats/movement-speed))
  (armor-save     [entity*] (effective-value entity* :stats/armor-save))
  (armor-pierce   [entity*] (effective-value entity* :stats/armor-pierce))
  (strength       [entity*] (effective-value entity* :stats/strength))
  )

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



