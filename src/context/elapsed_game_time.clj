(ns context.elapsed-game-time
  (:require [api.context :refer [stopped?]]))

(defrecord ImmutableCounter [duration stop-time])

(defn- elapsed-time [ctx]
  (-> ctx :context/game-state :elapsed-time))

(extend-type api.context.Context
  api.context/Counter
  (->counter [ctx duration]
    {:pre [(>= duration 0)]}
    (->ImmutableCounter duration (+ @(elapsed-time ctx) duration)))

  (stopped? [ctx {:keys [stop-time]}]
    (>= @(elapsed-time ctx) stop-time))

  (reset [ctx {:keys [duration] :as counter}]
    (assoc counter :stop-time (+ @(elapsed-time ctx) duration)))

  (finished-ratio [ctx {:keys [duration stop-time] :as counter}]
    {:post [(<= 0 % 1)]}
    (if (stopped? ctx counter)
      0
      ; min 1 because floating point math inaccuracies
      (min 1 (/ (- stop-time @(elapsed-time ctx)) duration))))

  (update-elapsed-game-time! [{:keys [context/delta-time] :as ctx}]
    (swap! (elapsed-time ctx) + delta-time)))
