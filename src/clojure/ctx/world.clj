(in-ns 'clojure.ctx)

(defprotocol Grid
  (cached-adjacent-cells [grid cell])
  (rectangle->cells [grid rectangle])
  (circle->cells    [grid circle])
  (circle->entities [grid circle]))

(defprotocol GridPointEntities
  (point->entities [ctx position]))

(defprotocol GridCell
  (blocked? [cell* z-order])
  (blocks-vision? [cell*])
  (occupied-by-other? [cell* entity]
                      "returns true if there is some occupying body with center-tile = this cell
                      or a multiple-cell-size body which touches this cell.")
  (nearest-entity          [cell* faction])
  (nearest-entity-distance [cell* faction]))

(defn cells->entities [cells*]
  (into #{} (mapcat :entities) cells*))

(defprotocol PRayCaster
  (ray-blocked? [ctx start target])
  (path-blocked? [ctx start target path-w] "path-w in tiles. casts two rays."))

(defprotocol Pathfinding
  (potential-fields-follow-to-enemy [ctx eid]))

(defprotocol ActiveEntities
  (active-entities [_]))

(defprotocol WorldGen
  (->world [ctx world-id]))

(defn world-delta
  "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed."
  [ctx]
  (:delta-time (:context/time ctx)))

(defn elapsed-time ; world-time, not counting different screens or paused world....
  "The elapsed in-game-time (not counting when game is paused)."
  [ctx]
  (:elapsed (:context/time ctx)))

(defn logic-frame ; starting with 0 ... ? when not doing anything
  "The game-logic frame number, starting with 1. (not counting when game is paused)"
  [ctx]
  (:logic-frame (:context/time ctx)))

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
