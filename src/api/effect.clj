(ns api.effect
  (:require [core.component :refer [defsystem]]))

(defsystem text [_ ctx])
(defsystem applicable? [_ ctx])

(defsystem useful? [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render-info [_ g ctx])
(defmethod render-info :default [_ g ctx])

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
