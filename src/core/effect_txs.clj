(ns core.effect-txs
  (:require [api.effect :as effect]))

; TODO
; text should work also w/o effect-ctx ...
; so work on 'effect' itself then ??
; strange effect-txs-or-effect ?
(defn text [effect-txs]
  (str/join "\n" (keep effect/text effect-txs)))
; (effect-txs/text (effect-txs/->insert-ctx hit-effect effect-ctx))
; maybe here just [effect effect-ctx] params?

; this is actually valid-effect-ctx?
(defn valid-params? [effect-txs]
  (every? effect/valid-params? effect-txs))

(defn useful? [effect-txs ctx]
  (some #(effect/useful? % ctx) effect-txs))

(defn render-info [g effect-txs]
  (run! #(effect/render-info % g) effect-txs))

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
