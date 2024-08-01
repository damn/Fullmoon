(ns effect.restore-hp-mana
  (:require [core.component :refer [defcomponent]]
            [data.val-max :refer [lower-than-max? set-to-max]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "effect" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (defcomponent component# {:widget :label
                                :schema [:= true]
                                :default-value true}
       (effect/text ~'[_ _ctx]
                    ~(str "Sets " (name stat) " to max."))

       (effect/valid-params? ~'[_ {:keys [effect/source]}]
                             ~'source)

       (effect/useful? ~'[_ {:keys [effect/source]}]
                       (lower-than-max? (~stat @~'source)))

       (transact! ~'[_ {:keys [effect/source]}]
                  [[:tx.entity/assoc ~'source ~stat (set-to-max (~stat @~'source))]]))))

(def-set-to-max-effect :entity/hp)
(def-set-to-max-effect :entity/mana)
