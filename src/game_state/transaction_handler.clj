(ns game-state.transaction-handler
  (:require graphics.image
            data.animation
            [api.context :refer [transact-all!]]
            [api.tx :refer [transact!]]))

(def ^:private record-txs? false)
(def ^:private frame->txs (atom nil))

(defn- add-tx-to-frame [frame->txs frame-num tx]
  (update frame->txs frame-num (fn [txs-at-frame]
                                 (if txs-at-frame
                                   (conj txs-at-frame tx)
                                   [tx]))))

(comment
 (= (-> {}
        (add-tx-to-frame 1 [:foo1 :bar1])
        (add-tx-to-frame 1 [:foo2 :bar2]))
    {1 [[:foo1 :bar1] [:foo2 :bar2]]})
 )

(def debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  (instance? graphics.image.Image %) "<Image>"
                  (instance? data.animation.ImmutableAnimation %) "<Animation>"
                  (instance? api.context.Context %) "<Context>"
                  :else %)
                tx)))

(extend-type api.context.Context
  api.context/TransactionHandler
  (set-record-txs! [_ bool]
    (.bindRoot #'record-txs? bool))

  (clear-recorded-txs! [_]
    (reset! frame->txs {}))

  (summarize-txs [_ txs]
    (clojure.pprint/pprint
     (for [[txkey txs] (group-by first txs)]
       [txkey (count txs)])))

  (transact-all! [ctx txs]
    (doseq [tx txs :when tx]
      (try (let [result (transact! tx ctx)]
             (if (and (nil? result)
                      (not= :tx.context.cursor/set (first tx)))
               (let [logic-frame (:logic-frame (:context/game ctx))]
                (when debug-print-txs?
                  (println @logic-frame "." (debug-print-tx tx)))
                (when record-txs?
                  (swap! frame->txs add-tx-to-frame @logic-frame tx)))
               (transact-all! ctx result)))
           (catch Throwable t
             (throw (ex-info "Error with transaction:" {:tx (debug-print-tx tx)} t))))))

  (frame->txs [_ frame-number]
    (@frame->txs frame-number)))
