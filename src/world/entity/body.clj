(ns world.entity.body
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.graphics.camera :as 🎥]
            [utils.core :refer [define-order ->tile]]
            [world.raycaster :refer [ray-blocked?]]))

(def z-orders [:z-order/on-ground
               :z-order/ground
               :z-order/flying
               :z-order/effect])

(def render-order (define-order z-orders))

(defn tile [entity*]
  (->tile (:position entity*)))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity*]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (🎥/position (g/world-camera))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (g/world-viewport-width))  2)))
     (<= ydist (inc (/ (float (g/world-viewport-height)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [source* target*]
  (and (or (not (:entity/player? source*))
           (on-screen? target*))
       (not (and los-checks?
                 (ray-blocked? (:position source*) (:position target*))))))
