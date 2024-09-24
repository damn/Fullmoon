(ns core.ctx.raycaster
  (:require [core.math.raycaster :as raycaster]
            [core.math.vector :as v]
            [data.grid2d :as grid2d]
            [core.ctx :refer :all]))

(defn- set-arr [arr cell* cell*->blocked?]
  (let [[x y] (:position cell*)]
    (aset arr x y (boolean (cell*->blocked? cell*)))))

(defcomponent :context/raycaster
  (->mk [[_ position->blocked?] {:keys [context/grid]}]
    (let [width  (grid2d/width  grid)
          height (grid2d/height grid)
          arr (make-array Boolean/TYPE width height)]
      (doseq [cell (grid2d/cells grid)]
        (set-arr arr @cell position->blocked?))
      (raycaster/map->ArrRayCaster {:arr arr
                                    :width width
                                    :height height}))))

; TO math.... // not tested
(defn- create-double-ray-endpositions
  "path-w in tiles."
  [[start-x start-y] [target-x target-y] path-w]
  {:pre [(< path-w 0.98)]} ; wieso 0.98??
  (let [path-w (+ path-w 0.02) ;etwas gr�sser damit z.b. projektil nicht an ecken anst�sst
        v (v/direction [start-x start-y]
                       [target-y target-y])
        [normal1 normal2] (v/get-normal-vectors v)
        normal1 (v/scale normal1 (/ path-w 2))
        normal2 (v/scale normal2 (/ path-w 2))
        start1  (v/add [start-x  start-y]  normal1)
        start2  (v/add [start-x  start-y]  normal2)
        target1 (v/add [target-x target-y] normal1)
        target2 (v/add [target-x target-y] normal2)]
    [start1,target1,start2,target2]))

(defn ray-blocked? [{:keys [context/raycaster]} start target]
  (raycaster/ray-blocked? raycaster start target))

(defn path-blocked?
  "path-w in tiles. casts two rays."
  [{:keys [context/raycaster]} start target path-w]
  (let [[start1,target1,start2,target2] (create-double-ray-endpositions start target path-w)]
    (or
     (raycaster/ray-blocked? raycaster start1 target1)
     (raycaster/ray-blocked? raycaster start2 target2))))
