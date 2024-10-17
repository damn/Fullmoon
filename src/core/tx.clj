(ns core.tx
  (:require [component.core :refer [defsystem]]))

(defsystem do!)

(defn- execute [tx]
  (cond (not tx) nil
        (fn? tx) (tx)
        :else (do! tx)))

(defn do-all [txs]
  (doseq [tx txs]
    (when-let [result (try (execute tx)
                           (catch Throwable t
                             (throw (ex-info "Error with transactions" {:tx tx} t))))]
      (do-all result))))
