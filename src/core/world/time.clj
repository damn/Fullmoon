(ns core.world.time
  (:require [core.ctx :refer :all]))

(def ^:private this :context/time)

(defcomponent this
  (->mk [_ _ctx]
    {:elapsed 0
     :logic-frame 0}))

(defn ^:no-doc update-time [ctx delta]
  (update ctx this #(-> %
                        (assoc :delta-time delta)
                        (update :elapsed + delta)
                        (update :logic-frame inc))))

(defn delta-time
  "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed."
  [ctx]
  (:delta-time (this ctx)))

(defn elapsed-time
  "The elapsed in-game-time (not counting when game is paused)."
  [ctx]
  (:elapsed (this ctx)))

(defn logic-frame
  "The game-logic frame number, starting with 1. (not counting when game is paused)"
  [ctx]
  (:logic-frame (this ctx)))

(defrecord Counter [duration stop-time])

(defn ->counter [ctx duration]
  {:pre [(>= duration 0)]}
  (->Counter duration (+ (elapsed-time ctx) duration)))

(defn stopped? [ctx {:keys [stop-time]}]
  (>= (elapsed-time ctx) stop-time))

(defn reset [ctx {:keys [duration] :as counter}]
  (assoc counter :stop-time (+ (elapsed-time ctx) duration)))

(defn finished-ratio [ctx {:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? ctx counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time (elapsed-time ctx)) duration))))
