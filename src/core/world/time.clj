(in-ns 'core.world)

(defcomponent :context/time
  (->mk [_ _ctx]
    {:elapsed 0
     :logic-frame 0}))

(defn update-time [ctx delta]
  (update ctx :context/time #(-> %
                                 (assoc :delta-time delta)
                                 (update :elapsed + delta)
                                 (update :logic-frame inc))))
