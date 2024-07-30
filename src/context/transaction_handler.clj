(ns context.transaction-handler
  (:require context.libgdx.image-drawer-creator
            [api.context :refer [transact-all!]]
            [api.tx :refer [transact!]]))

(def ^:private record-txs? false)

(defn set-record-txs! [bool]
  (.bindRoot #'record-txs? bool))

(def ^:private frame->txs (atom nil))

(defn clear-recorded-txs! []
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
                  (instance? context.libgdx.image_drawer_creator.Image %) "<Image>"
                  (instance? data.animation.ImmutableAnimation %) "<Animation>"
                  (instance? api.context.Context %) "<Context>"
                  :else %)
                tx)))

(extend-type api.context.Context
  api.context/TransactionHandler
  (transact-all! [{:keys [context/game-logic-frame] :as ctx} txs]
    (doseq [tx txs :when tx]
      (try (let [result (transact! tx ctx)]
             (if (and (nil? result)
                      (not= :tx/cursor (first tx)))
               (do
                (when debug-print-txs?
                  (println @game-logic-frame "." (debug-print-tx tx)))
                (when record-txs?
                  (swap! frame->txs add-tx-to-frame @game-logic-frame tx)))
               (transact-all! ctx result)))
           (catch Throwable t
             (throw (ex-info "Error with transaction:" {:tx (debug-print-tx tx)} t))))))

  (frame->txs [_ frame-number]
    (@frame->txs frame-number)))

(defn summarize-txs [txs]
  (clojure.pprint/pprint
   (for [[txkey txs] (group-by first txs)]
     [txkey (count txs)])))
