(ns world.time
  (:require [gdx.graphics :as graphics]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]))

(defcomponent :world/time {}
  (component/create [_ _ctx]
    {:elapsed 0
     :logic-frame 0}))

(defrecord Counter [duration stop-time])

(extend-type core.context.Context
  core.context/Time
  (update-time [ctx]
    (let [delta (min (graphics/delta-time) entity/max-delta-time)]
      (update ctx :world/time #(-> %
                                   (assoc :delta-time delta)
                                   (update :elapsed + delta)
                                   (update :logic-frame inc)))))

  (delta-time   [ctx] (:delta-time  (:world/time ctx)))
  (elapsed-time [ctx] (:elapsed     (:world/time ctx)))
  (logic-frame  [ctx] (:logic-frame (:world/time ctx)))

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
