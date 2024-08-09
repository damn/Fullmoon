(ns context.game.time
  (:require [api.context :as ctx]))

(defn ->build []
  {:context.game/max-delta-time 0.04
   :context.game/elapsed-time 0})

(defrecord Counter [duration stop-time])

(extend-type api.context.Context
  api.context/Time
  (delta-time     [ctx] (:context.game/delta-time     ctx))
  (max-delta-time [ctx] (:context.game/max-delta-time ctx))
  (elapsed-time   [ctx] (:context.game/elapsed-time   ctx))

  (->counter [ctx duration]
    {:pre [(>= duration 0)]}
    (->Counter duration (+ (ctx/elapsed-time ctx) duration)))

  (stopped? [ctx {:keys [stop-time]}]
    (>= (ctx/elapsed-time ctx) stop-time))

  (reset [ctx {:keys [duration] :as counter}]
    (assoc counter :stop-time (+ (ctx/elapsed-time ctx) duration)))

  (finished-ratio [ctx {:keys [duration stop-time] :as counter}]
    {:post [(<= 0 % 1)]}
    (if (ctx/stopped? ctx counter)
      0
      ; min 1 because floating point math inaccuracies
      (min 1 (/ (- stop-time (ctx/elapsed-time ctx)) duration)))) )

(defn update-time [ctx]
  (-> ctx
      (assoc :context.game/delta-time (min (ctx/delta-time-raw ctx)
                                           (ctx/max-delta-time ctx)))
      (update :context.game/elapsed-time + (ctx/delta-time ctx))))
