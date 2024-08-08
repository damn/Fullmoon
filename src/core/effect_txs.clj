(ns core.effect-txs
  (:require [api.effect :as effect]))

(defn text [effect]
  (str/join "\n" (keep effect/text effect)))

(defn valid-params? [effect]
  (every? effect/valid-params? effect))

(defn useful? [txs]
  (some #(effect/useful? % effect-ctx) txs))

(defn render-info [effect-ctx g txs]
  (run! #(effect/render-info % g effect-ctx) txs))

(defn ->insert-ctx
  "Inserts the effect-ctx as second element in each effect.
  Then the effect can be used as a vector of transactions.
  Called effect-txs."
  [effect effect-ctx]
  (vec (for [[k & vs] effect]
         (apply vector k effect-ctx vs))))

(comment
 (=
  (let [effect [[:effect/melee-damage true]
                [:effect/sound "asd.wav"]]
        effect-ctx {:effect/source :foo
                    :effect/target :bar}]
    (->txs effect effect-ctx))
  [[:effect/melee-damage #:effect{:source :foo, :target :bar} true]
   [:effect/sound #:effect{:source :foo, :target :bar} "asd.wav"]])
 )
