(ns core.effect
  (:require [core.component :refer [defsystem]]))

(def ^:private ^:dbg-flag debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  :else %)
                tx)))

#_(defn- tx-happened! [tx]
    (when (and
           (not (fn? tx))
           (not= :tx/cursor (first tx)))
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx)))))

(defsystem do!
  "Return nil or new coll/seq of txs to be done recursively."
  [_])

(defn effect!
  "An effect is defined as a sequence of txs(transactions).

A tx is either a (fn []) with no args or a component which implements the do! system.

All txs are being executed in sequence, any nil are skipped.

If the result of a tx is non-nil, we assume a new sequence of txs and effect! calls itself recursively.

On any exception we get a stacktrace with all tx's values and names shown."
  [effect]
  (doseq [tx effect
          :when tx]
    (try (when-let [result (if (fn? tx)
                             (tx)
                             (do! tx))]
           (effect! result))
         (catch Throwable t
           (throw (ex-info "Error with transaction"
                           {:tx tx #_(debug-print-tx tx)}
                           t))))))

