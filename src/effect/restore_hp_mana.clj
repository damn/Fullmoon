(ns effect.restore-hp-mana
  (:require [core.component :refer [defcomponent]]
            [data.val-max :refer [lower-than-max? set-to-max]]
            [api.effect :as effect]))

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "effect" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (defcomponent component# {:widget :label
                               :schema [:= true]
                               :default-value true}
       (effect/text ~'[_ _effect-ctx]
         ~(str "Sets " (name stat) " to max."))

       (effect/valid-params? ~'[_ {:keys [effect/source]}]
         ~'source)

       (effect/useful? ~'[_ {:keys [effect/source]} _ctx]
         (lower-than-max? (~stat @~'source)))

       (effect/txs ~'[_ {:keys [effect/source]}]
         [[:tx.entity/assoc ~'source ~stat (set-to-max (~stat @~'source))]]))))

(def-set-to-max-effect :entity/hp)
(def-set-to-max-effect :entity/mana)

; effect
; key       - :entity/hp
; operation - set-to-max (because val-max)
; value = here no value

#_(defcomponent :effect/set-to-max {:widget :label
                                  :schema [:= true]
                                  :default-value true}
  (effect/text [[_ stat] _effect-ctx]
    (str "Sets " (name stat) " to max."))

  (effect/valid-params? [_ {:keys [effect/source]}]
    source)

  (effect/useful? [[_ stat] {:keys [effect/source]} _ctx]
    (lower-than-max? (stat @source)))

  (effect/txs [[_ stat] {:keys [effect/source]}]
    [[:tx.entity/assoc source stat (set-to-max (stat @source))]]))

; this as macro ... ? component which sets the value of another component ??
#_(defcomponent :effect/set-mana-to-max {:widget :label
                                         :schema [:= true]
                                         :default-value true}
  (effect/valid-params? [_ {:keys [effect/source]}] source)
  (effect/text    [_ _effect-ctx]     (effect/text    [:effect/set-to-max :entity/mana]))
  (effect/useful? [_ effect-ctx _ctx] (effect/useful? [:effect/set-to-max :entity/mana] effect-ctx))
  (effect/txs     [_ effect-ctx]      (effect/txs     [:effect/set-to-max :entity/mana] effect-ctx)))

#_[:effect/set-to-max :entity/hp]
#_[:effect/set-to-max :entity/mana]
