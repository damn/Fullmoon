(in-ns 'clojure.ctx)

(def ctx-time :context/time)

(defn world-delta
  "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed."
  [ctx]
  (:delta-time (ctx-time ctx)))

(defn elapsed-time ; world-time, not counting different screens or paused world....
  "The elapsed in-game-time (not counting when game is paused)."
  [ctx]
  (:elapsed (ctx-time ctx)))

(defn logic-frame ; starting with 0 ... ? when not doing anything
  "The game-logic frame number, starting with 1. (not counting when game is paused)"
  [ctx]
  (:logic-frame (ctx-time ctx)))

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
