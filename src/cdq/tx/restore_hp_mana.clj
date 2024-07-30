(ns cdq.tx.restore-hp-mana
  (:require [core.component :as component]
            [data.val-max :refer [lower-than-max? set-to-max]]
            [cdq.api.context :refer [transact!]]
            [cdq.api.effect :as effect]))

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "tx" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (component/def component# {:widget :label
                                :schema [:= true]
                                :default-value true}
       ~'_
       (effect/text ~'[_ _ctx]
                    ~(str "Sets " (name stat) " to max."))

       (effect/valid-params? ~'[_ {:keys [effect/source]}]
                             ~'source)

       (effect/useful? ~'[_ {:keys [effect/source]}]
                       (lower-than-max? (~stat @~'source)))

       (transact! ~'[_ {:keys [effect/source]}]
                  [[:tx/assoc ~'source ~stat (set-to-max (~stat @~'source))]]))))

(def-set-to-max-effect :entity/hp)
(def-set-to-max-effect :entity/mana)
