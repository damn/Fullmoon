(in-ns 'clojure.ctx)

(def ^:private ^:dbg-flag debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  :else %)
                tx)))

#_(defn- tx-happened! [tx ctx]
  (when (and
         (not (fn? tx))
         (not= :tx/cursor (first tx)))
    (let [logic-frame (time/logic-frame ctx)] ; only if debug or record deref this?
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx))))))

(declare effect!)

(defsystem do!
  " 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
  when returning a 'map?'

  2. return seq of txs -> those txs will be done recursively
  2.1 also seq of fns wih [ctx] param can be passed.

  3. return nil in case of doing nothing -> will just continue with existing ctx.

  do NOT do a effect/do inside a effect/do! because then we have to return a context
  and that means that transaction will be recorded and done double with all the sub-transactions
  in the replay mode
  we only want to record actual side effects, not transactions returning other lower level transactions"
  [_ ctx])

(defn- handle-tx! [ctx tx]
  (let [result (if (fn? tx)
                 (tx ctx)
                 (do! tx ctx))]
    (if (map? result) ; new context
      (do
       #_(tx-happened! tx ctx)
       result)
      (effect! ctx result))))

(defn effect! [ctx txs]
  (reduce (fn [ctx tx]
            (if (nil? tx)
              ctx
              (try
               (handle-tx! ctx tx)
               (catch Throwable t
                 (throw (ex-info "Error with transaction"
                                 {:tx tx #_(debug-print-tx tx)}
                                 t))))))
          ctx
          txs))
