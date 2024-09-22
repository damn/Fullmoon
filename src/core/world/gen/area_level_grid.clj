(ns core.world.gen.area-level-grid
  (:require [data.grid2d :as grid]))

; can adjust:
; * split percentage , for higher level areas may scale faster (need to be more careful)
; * not 4 neighbors but just 1 tile randomwalk -> possible to have lvl 9 area next to lvl 1 ?
; * adds metagame to the game , avoid/or fight higher level areas, which areas to go next , etc...
; -> up to the player not step by step level increase like D2
; can not only take first of added-p but multiples also
; can make parameter how fast it scales
; area-level-grid works better with more wide grids
; if the cave is very straight then it is just a continous progression and area-level-grid is useless
(defn ->area-level-grid
  "Expands from start position by adding one random adjacent neighbor.
  Each random walk is a step and is assigned a level as of max-level.
  (Levels are scaled, for example grid has 100 ground cells, so steps would be 0 to 100(99?)
  and max-level will smooth it out over 0 to max-level.
  The point of this is to randomize the levels so player does not have a smooth progression
  but can encounter higher level areas randomly around but there is always a path which goes from
  level 0 to max-level, so the player has to decide which areas to do in which order."
  [& {:keys [grid start max-level walk-on]}]
  (let [maxcount (->> grid
                      grid/cells
                      (filter walk-on)
                      count)
        ; -> assume all :ground cells can be reached from start
        ; later check steps count == maxcount assert
        level-step (/ maxcount max-level)
        step->level #(int (Math/ceil (/ % level-step)))
        walkable-neighbours (fn [grid position]
                              (filter #(walk-on (get grid %))
                                      (grid/get-4-neighbour-positions position)))]
    (loop [next-positions #{start}
           steps          [[0 start]]
           grid           (assoc grid start 0)]
      (let [next-positions (set
                            (filter #(seq (walkable-neighbours grid %))
                                    next-positions))]
        (if (seq next-positions)
          (let [p (rand-nth (seq next-positions))
                added-p (rand-nth (walkable-neighbours grid p))]
            (if added-p
              (let [area-level (step->level (count steps))]
                (recur (conj next-positions added-p)
                       (conj steps [area-level added-p])
                       (assoc grid added-p area-level)))
              (recur next-positions
                     steps
                     grid)))
          {:steps steps
           :area-level-grid grid})))))
