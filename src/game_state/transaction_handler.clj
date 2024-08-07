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

  ; takes a seq ( not only vector )
  ; of txs (or nils, then skipped)
  ; or nil if we do a real side effect and not pass down to other txs

  ; but why do txs which call itself transact-all! return empty vector?
  ; because probably don't want to be recorded themself

  ; only nils are recorded, the real changes.... I think

  ; so instead of nil we just pass a context back for real transactions?
  ; and reduce over ctx

  ; don't want to do if instance check
  ; can I just derive from the type
  ; then I can find the side-effect transactions also easier with grep...
  ;(instance? api.context.Context (api.context/->Context))

  ; reduce lazy ?

  ; TODO what at the places where empty vector is returned (dont record?)
  (transact-all! [ctx txs]
    ;(println "transact-all! with txs: " txs " and ctx " (class ctx))
    (let [rslt (reduce (fn [ctx tx]
              ;(println "inside reduce transact-all! with tx " tx " and ctx: " (class ctx))
              (if (nil? tx)
                ctx
                (try
                 (let [result (transact! tx ctx)]
                   ;(println "Result of transact! inside tx handler: " (class result))
                   (assert result
                           (str "transact! returned nil or falsey: " (debug-print-tx tx))
                           )
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

                     (try
                      (do
                       ;(println "no map -> transact-all! with result: " (class result))
                       (ctx/transact-all! ctx result))
                          (catch Throwable t
                            (throw (ex-info "Error with tx result:" {:result result} t)))
                          )))
                     (catch Throwable t
                       (throw (ex-info "Error with transaction:" {:tx (debug-print-tx tx)} t))))
                )
              )
            ctx
            txs
            )]

      ;(println "Return value of tx-handler/transact-all!: " (class rslt) " of txs: " txs)
      rslt
      )

    )

  (frame->txs [_ frame-number]
    (@frame->txs frame-number)))
