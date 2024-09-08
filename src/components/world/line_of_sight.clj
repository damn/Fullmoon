(ns components.world.line-of-sight
  (:require [gdx.graphics.camera :as camera]
            [core.context :as ctx]))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity* ctx]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera/position (ctx/world-camera ctx))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (ctx/world-viewport-width ctx))  2)))
     (<= ydist (inc (/ (float (ctx/world-viewport-height ctx)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

(extend-type core.context.Context
  core.context/WorldLineOfSight
  ; does not take into account size of entity ...
  ; => assert bodies <1 width then
  (line-of-sight? [context source* target*]
    (and (or (not (:entity/player? source*))
             (on-screen? target* context))
         (not (and los-checks?
                   (ctx/ray-blocked? context (:position source*) (:position target*)))))))
