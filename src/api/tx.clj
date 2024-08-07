(ns api.tx
  (:require [core.component :refer [defsystem]]))

; 1. return new ctx if we change something or have side effect -> will be recorded
; 2. return seq of txs if we do a nested transact-all!
; 3. if we do side effects with transact-all! return empty vector [] instead of ctx
; so will not be recorded double
; => we record only real side effect-y txs
(defsystem transact! [_ ctx])

(comment
 (clojure.pprint/pprint
  (sort (keys (methods transact!))))
 )
