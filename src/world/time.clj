(ns world.time)

(declare paused?)

(declare ^{:doc "The game logic update delta-time. Different then clojure.gdx.graphics/delta-time because it is bounded by a maximum value for entity movement speed."}
         world-delta

         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time

         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defn init! []
  (.bindRoot #'elapsed-time 0)
  (.bindRoot #'logic-frame 0))

(defn update! [delta]
  (.bindRoot #'world-delta delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defn ->counter [duration]
  {:pre [(>= duration 0)]}
  {:duration duration
   :stop-time (+ elapsed-time duration)})

(defn stopped? [{:keys [stop-time]}]
  (>= elapsed-time stop-time))

(defn reset [{:keys [duration] :as counter}]
  (assoc counter :stop-time (+ elapsed-time duration)))

(defn finished-ratio [{:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time elapsed-time) duration))))
