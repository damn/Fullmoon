(ns game-state.elapsed-time
  (:require [api.context :refer [stopped?]]))

(defn ->state []
  {:elapsed-time (atom 0)})

(defn- state [ctx]
  (-> ctx :context/game :elapsed-time))

(defrecord ImmutableCounter [duration stop-time])

(extend-type api.context.Context
  api.context/Counter
  (->counter [ctx duration]
    {:pre [(>= duration 0)]}
    (->ImmutableCounter duration (+ @(state ctx) duration)))

  (stopped? [ctx {:keys [stop-time]}]
    (>= @(state ctx) stop-time))

  (reset [ctx {:keys [duration] :as counter}]
    (assoc counter :stop-time (+ @(state ctx) duration)))

  (finished-ratio [ctx {:keys [duration stop-time] :as counter}]
    {:post [(<= 0 % 1)]}
    (if (stopped? ctx counter)
      0
      ; min 1 because floating point math inaccuracies
      (min 1 (/ (- stop-time @(state ctx)) duration))))

  (update-elapsed-game-time! [{:keys [context/delta-time] :as ctx}]
    (swap! (state ctx) + delta-time)))
