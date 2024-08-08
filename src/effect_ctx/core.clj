(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.effect :as effect]))

; TODO maybe even the player/npc effect ctx create here
; together in 1 place
; with schema ?

(defn valid-params? [effect-ctx effect]
  (every? #(effect/valid-params? % effect-ctx) effect))

(defn useful? [effect-ctx effect ctx]
  (some #(effect/useful? % effect-ctx ctx) effect))

(defn txs [effect-ctx effect]
  (mapcat #(effect/txs % effect-ctx) effect))

; TODO called only in 1 place? -> no need to put fn ?
(defn render-info [effect-ctx g effect]
  (run! #(effect/render-info % effect-ctx g) effect-txs))


; TODO
; text should work also w/o effect-ctx ...
; so work on 'effect' itself then ??
; strange effect-txs-or-effect ?
(defn text [effect-ctx effect]
  (str/join "\n" (keep #(effect/text % effect-ctx) effect)))
; (effect-txs/text (effect-txs/->insert-ctx hit-effect effect-ctx))
; maybe here just [effect effect-ctx] params?

; this is actually valid-effect-ctx?


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
