(ns api.effect
  (:require [core.component :refer [defsystem]]))

(defsystem text [_ effect-ctx])
(defsystem applicable? [_ effect-ctx])

(defsystem useful?          [_ effect-ctx ctx]) ; used for NPCs
(defmethod useful? :default [_ effect-ctx ctx] true)

(defsystem render-info          [_ effect-ctx g])
(defmethod render-info :default [_ effect-ctx g])

; 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
; when returning a 'map?'

; 2. return seq of txs -> those txs will be done recursively

; 3. return nil in case of doing nothing -> will just continue with existing ctx.

; do NOT do a ctx/do! inside a effect/do! because then we have to return a context
; and that means that transaction will be recorded and done double with all the sub-transactions
; in the replay mode
; we only want to record actual side effects, not transactions returning other lower level transactions
(defsystem do! [_ ctx])

(comment
 (clojure.pprint/pprint
  (sort (keys (methods do!))))
 )
