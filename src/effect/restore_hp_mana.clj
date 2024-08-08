(ns effect.restore-hp-mana
  (:require [core.component :refer [defcomponent]]
            [data.val-max :refer [lower-than-max? set-to-max]]
            [api.effect :as effect]))

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "effect" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (defcomponent component# {:widget :label
                               :schema [:= true]
                               :default-value true}
       (effect/valid-params? ~'[_ {:keys [effect/source]}]
         ~'source)

       (effect/useful? ~'[_ {:keys [effect/source]} _ctx]
         (lower-than-max? (~stat @~'source)))

       (effect/text ~'[_ _effect-ctx]
         ~(str "Sets " (name stat) " to max."))

       (effect/txs ~'[_ {:keys [effect/source]}]
         [[:tx.entity/assoc ~'source ~stat (set-to-max (~stat @~'source))]]))))

(def-set-to-max-effect :entity/hp)
(def-set-to-max-effect :entity/mana)
