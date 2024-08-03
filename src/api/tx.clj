(ns api.tx
  (:require [core.component :refer [defsystem]]))

(defsystem transact! [_ ctx])

(comment
 (clojure.pprint/pprint
  (sort (keys (methods transact!))))
 )
