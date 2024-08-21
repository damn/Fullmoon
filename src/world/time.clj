(ns world.time
  (:require [gdx.graphics :as graphics]
            [api.context :as ctx]))

(defn ->build []
  {::elapsed-time 0
   ::logic-frame 0})

(defrecord Counter [duration stop-time])

(extend-type api.context.Context
  api.context/Time
  (delta-time     [ctx] (::delta-time   ctx))
  (elapsed-time   [ctx] (::elapsed-time ctx))
  (logic-frame    [ctx] (::logic-frame  ctx))

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
  (let [delta (min (graphics/delta-time) ctx/max-delta-time)]
    (-> ctx
        (assoc ::delta-time delta)
        (update ::elapsed-time + delta)
        (update ::logic-frame inc))))
