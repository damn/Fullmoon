(ns cdq.modifier.all
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [core.component :as component]
            [data.val-max :refer [apply-max]]
            [cdq.api.modifier :as modifier]
            [cdq.attributes :as attr]))

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
(component/def :modifier/max-hp {:widget :text-field :schema number?}
  amount
  (modifier/text [_] (plus-max-modifier-text "HP" amount))
  (modifier/keys [_] [:entity/hp])
  (modifier/apply   [_ hp] (apply-max-plus  hp amount))
  (modifier/reverse [_ hp] (apply-max-minus hp amount)))

; TODO has to be integer ?
(component/def :modifier/max-mana {:widget :text-field :schema number?}
  amount
  (modifier/text [_] (plus-max-modifier-text "Mana" amount))
  (modifier/keys [_] [:entity/mana])
  (modifier/apply   [_ mana] (apply-max-plus  mana amount))
  (modifier/reverse [_ mana] (apply-max-minus mana amount)))

(defn- actions-speed-percent [v]
  (str (check-plus-symbol v) (int (* 100 v))))

(component/def :modifier/cast-speed attr/pos-attr
  amount
  (modifier/text [_] (str (actions-speed-percent amount) "% Casting-Speed"))
  (modifier/keys [_] [:entity/stats :stats/cast-speed])
  (modifier/apply   [_ value] (+ (or value 1) amount))
  (modifier/reverse [_ value] (- value amount)))

(component/def :modifier/attack-speed attr/pos-attr
  amount
  (modifier/text [_] (str (actions-speed-percent amount) "% Attack-Speed"))
  (modifier/keys [_] [:entity/stats :stats/attack-speed])
  (modifier/apply   [_ value] (+ (or value 1) amount))
  (modifier/reverse [_ value] (- value amount)))

; TODO move into down modifier/text and common fn ... percent etc. anyway use integer later
; also negative possible ?!
(defn- armor-modifier-text [modifier-attribute delta]
  (str/join " "
            [(str "+" (int (* delta 100)) "%")  ; TODO signum ! negativ possible?
             (name modifier-attribute)
             ]))

; TODO no schema
(component/def :modifier/armor-save {:widget :text-field :schema :some}
  delta
  (modifier/text [[k _]] (armor-modifier-text k delta))
  (modifier/keys [_] [:entity/stats :stats/armor-save])
  (modifier/apply   [_ value] (+ (or value 0) delta))
  (modifier/reverse [_ value] (- value delta)))

; TODO no schema
(component/def :modifier/armor-pierce {:widget :text-field :schema :some}
  delta
  (modifier/text [[k _]] (armor-modifier-text delta))
  (modifier/keys [_] [:entity/stats :stats/armor-pierce])
  (modifier/apply   [_ value] (+ (or value 0) delta))
  (modifier/reverse [_ value] (- value delta)))

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

(component/def :modifier/damage {:widget :text-field :schema :some}  ; TODO no schema
  value
  (modifier/text [_]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (damage-modifier-text value))
  (modifier/keys [_] [:entity/stats :stats/damage])
  (modifier/apply [_ stat]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (update-in stat (drop-last value) #(+ (or % (default-value (get value 1)))
                                          (last value))))
  (modifier/reverse [_ stat]
    (assert (check-damage-modifier-value value)
            (str "Wrong value for damage modifier: " value))
    (update-in stat (drop-last value) - (last value))))
