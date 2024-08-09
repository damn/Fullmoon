(ns context.game.transaction-handler
  (:require context.graphics.image
            data.animation
            [api.context :as ctx]
            [api.tx :refer [transact!]]))

(def ^:private record-txs? false)
(def ^:private frame->txs (atom nil))

(defn- set-record-txs! [bool]
  (.bindRoot #'record-txs? bool))

(defn- clear-recorded-txs! []
  (reset! frame->txs {}))

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
                  (instance? context.graphics.image.Image %) "<Image>"
                  (instance? data.animation.ImmutableAnimation %) "<Animation>"
                  (instance? api.context.Context %) "<Context>"
                  :else %)
                tx)))

(defn- tx-happened! [tx ctx]
  (when (not= :tx.context.cursor/set (first tx))
    (let [logic-frame (ctx/logic-frame ctx)] ; TODO only if debug or record deref this...
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx)))
      (when record-txs?
        (swap! frame->txs add-tx-to-frame logic-frame tx)))))

(extend-type api.context.Context
  api.context/TransactionHandler
  (summarize-txs [_ txs]
    (clojure.pprint/pprint
     (for [[txkey txs] (group-by first txs)]
       [txkey (count txs)])))

  (transact-all! [ctx txs]
    (reduce (fn [ctx tx]
              (if (nil? tx)
                ctx
                (try
                 (let [result (transact! tx ctx)]
                   (if (map? result) ; probably faster than (instance? api.context.Context result)
                     (do
                      (tx-happened! tx ctx)
                      result)
                     (ctx/transact-all! ctx result)))
                 (catch Throwable t
                   (throw (ex-info "Error with transaction:" {:tx (debug-print-tx tx)} t))))))
            ctx
            txs))

  (frame->txs [_ frame-number]
    (@frame->txs frame-number)))

(defn initialize! [game-loop-mode]
  (case game-loop-mode
    :game-loop/normal (do
                       (clear-recorded-txs!)
                       (set-record-txs! true))
    :game-loop/replay (do
                       (assert record-txs?)
                       (set-record-txs! false)
                       ;(println "Initial entity txs:")
                       ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
                       )))
