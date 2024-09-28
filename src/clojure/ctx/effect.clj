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
