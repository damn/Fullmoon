(ns context.elapsed-game-time
  (:require gdl.context
            [cdq.api.context :refer [stopped?]]))

(defrecord ImmutableCounter [duration stop-time])

(extend-type gdl.context.Context
  cdq.api.context/Counter
  (->counter [{:keys [context/elapsed-game-time]} duration]
    {:pre [(>= duration 0)]}
    (->ImmutableCounter duration
                        (+ @elapsed-game-time duration)))

  (stopped? [{:keys [context/elapsed-game-time]}
             {:keys [stop-time]}]
    (>= @elapsed-game-time stop-time))

  (reset [{:keys [context/elapsed-game-time]}
          {:keys [duration] :as counter}]
    (assoc counter :stop-time (+ @elapsed-game-time duration)))

  (finished-ratio [{:keys [context/elapsed-game-time] :as context}
                   {:keys [duration stop-time] :as counter}]
    {:post [(<= 0 % 1)]}
    (if (stopped? context counter)
      0
      ; min 1 because floating point math inaccuracies
      (min 1 (/ (- stop-time @elapsed-game-time) duration))))

  (update-elapsed-game-time! [{:keys [context/elapsed-game-time
                                      context/delta-time]}]
    (swap! elapsed-game-time + delta-time)))
