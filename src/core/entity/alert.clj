(in-ns 'core.entity)

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter ctx delay-seconds)
        :faction faction}}]]))
