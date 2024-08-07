(ns game-state.transaction-handler
  (:require graphics.image
            data.animation
            [api.context :as ctx]
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

  ; takes a seq ( not only vector ) of txs (or nils, then skipped)
  ; or returns a new ctx (map? faster than instance??)
  ; TODO recording which ones now? e.g. tx/effect not base lvl tx but returning ctx
  ; derive from keyword or sth ?
  ; => make for a way to find it also easier with grep the real txs
  ;(instance? api.context.Context (api.context/->Context))
  (transact-all! [ctx txs]
    (reduce (fn [ctx tx]
              (if (nil? tx)
                ctx
                (try
                 (let [result (transact! tx ctx)]
                   (if (and (map? result)
                            #_(not= :tx.context.cursor/set (first tx)))
                     (do
                      #_(let [logic-frame (:context.game/logic-frame ctx)] ; TODO only if debug or record deref this...
                          (when debug-print-txs?
                            (println logic-frame "." (debug-print-tx tx)))
                          (when record-txs?
                            (swap! frame->txs add-tx-to-frame logic-frame tx)))
                      ;(println "map -> return rslt")
                      result)
                     (ctx/transact-all! ctx result)))
                 (catch Throwable t
                   (throw (ex-info "Error with transaction:" {:tx (debug-print-tx tx)} t))))))
            ctx
            txs))

  (frame->txs [_ frame-number]
    (@frame->txs frame-number)))
