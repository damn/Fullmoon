(ns modifier.all
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [core.component :refer [defcomponent]]
            [data.val-max :refer [apply-max]]
            [api.modifier :as modifier]
            [core.data :as data]))

; TODO add movement speed +/- modifier.

; TODO consistent name: 'delta' & 'value'

; TODO modifier is always for a stat? move hp/mana into stats? and modifier/stat somehow
; make common code/namings?! lets see...

(defn- check-plus-symbol [n]
  (case (math/signum n)
    (0.0 1.0) "+"
    -1.0 ""))

(defn- plus-max-modifier-text [modified-value-name v]
  (str (check-plus-symbol v) v " " modified-value-name))

(defn- apply-max-plus  [vmx v] (apply-max vmx #(+ % v)))
(defn- apply-max-minus [vmx v] (apply-max vmx #(- % v)))

; TODO has to be integer ?
(defcomponent :modifier/max-hp {:widget :text-field :schema number?}
  (modifier/text [[_ delta]] (plus-max-modifier-text "HP" delta))
  (modifier/keys [_] [:entity/hp])
  (modifier/apply   [[_ delta] hp] (apply-max-plus  hp delta))
  (modifier/reverse [[_ delta] hp] (apply-max-minus hp delta)))

; TODO has to be integer ?
(defcomponent :modifier/max-mana {:widget :text-field :schema number?}
  (modifier/text [[_ delta]] (plus-max-modifier-text "Mana" delta))
  (modifier/keys [_] [:entity/mana])
  (modifier/apply   [[_ delta] mana] (apply-max-plus  mana delta))
  (modifier/reverse [[_ delta] mana] (apply-max-minus mana delta)))

(defn- actions-speed-percent [v]
  (str (check-plus-symbol v) (int (* 100 v))))

(defcomponent :modifier/cast-speed data/pos-attr
  (modifier/text [[_ delta]] (str (actions-speed-percent delta) "% Casting-Speed"))
  (modifier/keys [_] [:entity/stats :stats/cast-speed])
  (modifier/apply   [[_ delta] value] (+ (or value 1) delta))
  (modifier/reverse [[_ delta] value] (- value delta)))

(defcomponent :modifier/attack-speed data/pos-attr
  (modifier/text [[_ delta]] (str (actions-speed-percent delta) "% Attack-Speed"))
  (modifier/keys [_] [:entity/stats :stats/attack-speed])
  (modifier/apply   [[_ delta] value] (+ (or value 1) delta))
  (modifier/reverse [[_ delta] value] (- value delta)))

; TODO move into down modifier/text and common fn ... percent etc. anyway use integer later
; also negative possible ?!
(defn- armor-modifier-text [modifier-attribute delta]
  (str/join " "
            [(str "+" (int (* delta 100)) "%")  ; TODO signum ! negativ possible?
             (name modifier-attribute)
             ]))

; TODO no schema
(defcomponent :modifier/armor-save {:widget :text-field :schema :some}
  (modifier/text [[k delta]] (armor-modifier-text k delta))
  (modifier/keys [_] [:entity/stats :stats/armor-save])
  (modifier/apply   [[_ delta] value] (+ (or value 0) delta))
  (modifier/reverse [[_ delta] value] (- value delta)))

; TODO no schema
(defcomponent :modifier/armor-pierce {:widget :text-field :schema :some}
  (modifier/text [[k delta]] (armor-modifier-text delta))
  (modifier/keys [_] [:entity/stats :stats/armor-pierce])
  (modifier/apply   [[_ delta] value] (+ (or value 0) delta))
  (modifier/reverse [[_ delta] value] (- value delta)))

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
