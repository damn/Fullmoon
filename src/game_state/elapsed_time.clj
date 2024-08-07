(ns game-state.elapsed-time
  (:require [api.context :as ctx]))

(defn ->state []
  {:elapsed-time 0})

(defn- elapsed-time [ctx]
  (-> ctx
      :context/game
      deref
      :elapsed-time))

(defrecord ImmutableCounter [duration stop-time])

(extend-type api.context.Context
  api.context/Counter
  (->counter [ctx duration]
    {:pre [(>= duration 0)]}
    (->ImmutableCounter duration (+ (elapsed-time ctx) duration)))

  (stopped? [ctx {:keys [stop-time]}]
    (>= (elapsed-time ctx) stop-time))

  (reset [ctx {:keys [duration] :as counter}]
    (assoc counter :stop-time (+ (elapsed-time ctx) duration)))

  (finished-ratio [ctx {:keys [duration stop-time] :as counter}]
    {:post [(<= 0 % 1)]}
    (if (ctx/stopped? ctx counter)
      0
      ; min 1 because floating point math inaccuracies
      (min 1 (/ (- stop-time (elapsed-time ctx)) duration)))))

(defn update-time [game-state*]
  (update game-state* :elapsed-time + (:delta-time game-state*))) ; don't use ctx/delta-time here because we don't have recent context at this state....
