(ns components.world.effect-handler
  (:require core.image
            core.animation
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(def ^:private record-txs? false)
(def ^:private frame->txs (atom nil))

(defn- clear-recorded-txs! []
  (reset! frame->txs {}))

(defcomponent :world/effect-handler
  (component/create [[_ [game-loop-mode record-transactions?]] _ctx]
    (case game-loop-mode
      :game-loop/normal (when record-transactions?
                          (clear-recorded-txs!)
                          (.bindRoot #'record-txs? true))
      :game-loop/replay (do
                         (assert record-txs?)
                         (.bindRoot #'record-txs? false)
                         ;(println "Initial entity txs:")
                         ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
                         ))
    nil))

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

(def ^:private ^:dbg-flag debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  (instance? core.image.Image %) "<Image>"
                  (instance? core.animation.ImmutableAnimation %) "<Animation>"
                  (instance? core.context.Context %) "<Context>"
                  :else %)
                tx)))

(defn- tx-happened! [tx ctx]
  (when (and
         (not (fn? tx))
         (not= :tx/cursor (first tx)))
    (let [logic-frame (ctx/logic-frame ctx)] ; only if debug or record deref this?
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx)))
      (when (and record-txs?
                 (not= (first tx) :tx/effect))
        (swap! frame->txs add-tx-to-frame logic-frame tx)))))

(defn- handle-tx! [ctx tx]
  (let [result (if (fn? tx)
                 (tx ctx)
                 (component/do! tx ctx))]
    (if (map? result) ; probably faster than (instance? core.context.Context result)
      (do
       (tx-happened! tx ctx)
       result)
      (ctx/do! ctx result))))

(extend-type core.context.Context
  core.context/EffectHandler
  (do! [ctx txs]
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

  (summarize-txs [_ txs]
    (clojure.pprint/pprint
     (for [[txkey txs] (group-by first txs)]
       [txkey (count txs)])))

  (frame->txs [_ frame-number]
    (@frame->txs frame-number))

  (effect-applicable? [ctx effects]
    (some #(component/applicable? % ctx) effects)))
