(ns mapgen.utils
  (:require [data.grid2d :as grid2d]))

(defn scale-grid [grid [w h]]
  (grid2d/create-grid (* (grid2d/width grid)  w)
                      (* (grid2d/height grid) h)
                      (fn [[x y]]
                        (get grid
                             [(int (/ x w))
                              (int (/ y h))]))))

(defn scalegrid [grid factor]
  (grid2d/create-grid (* (grid2d/width grid) factor)
                      (* (grid2d/height grid) factor)
                      (fn [posi]
                        (get grid (mapv #(int (/ % factor)) posi)))))
; TODO other keys in the map-grid are lost -> look where i transform grids like this
; merge with ld-grid?

(defn create-borders-positions [grid] ; TODO not distinct -> apply distinct or set
  (let [w (grid2d/width grid),h (grid2d/height grid)]
    (concat
      (mapcat (fn [x] [[x 0] [x (dec h)]]) (range w))
      (mapcat (fn [y] [[0 y] [(dec w) y]]) (range h)))))

(defn get-3x3-cellvalues [grid posi]
  (map grid (cons posi (grid2d/get-8-neighbour-positions posi))))

(defn not-border-position? [[x y] grid]
  (and (>= x 1) (>= y 1)
       (< x (dec (grid2d/width grid)))
       (< y (dec (grid2d/height grid)))))

(defn border-position? [p grid] (not (not-border-position? p grid)))

(defn wall-at? [grid posi]
  (= :wall (get grid posi)))

(defn undefined-value-behind-walls
  "also border positions set to undefined where there are nil values"
  [grid]
  (grid2d/transform grid
                    (fn [posi value]
                      (if (and (= :wall value)
                               (every? #(let [value (get grid %)]
                                          (or (= :wall value) (nil? value)))
                                       (grid2d/get-8-neighbour-positions posi)))
                        :undefined
                        value))))

; if no tile
; and some has tile at get-8-neighbour-positions
; -> should be a wall
; -> paint in tiled editor set tile at cell and layer
; -> texture ?
; spritesheet already available ?!

(defn fill-single-cells [grid] ; TODO removes single walls without adjacent walls
  (grid2d/transform grid
                    (fn [posi value]
                      (if (and (not-border-position? posi grid)
                               (= :wall value)
                               (not-any? #(wall-at? grid %)
                                         (grid2d/get-4-neighbour-positions posi)))
                        :ground
                        value))))

(defn- print-cell [celltype]
  (print (if (number? celltype)
           celltype
           (case celltype
             nil               "?"
             :undefined        " "
             :ground           "_"
             :wall             "#"
             :airwalkable      "."
             :module-placement "X"
             :start-module     "@"
             :transition       "+"))))

; print-grid in data.grid2d is y-down
(defn printgrid
  "Prints with y-up coordinates."
  [grid]
  (doseq [y (range (dec (grid2d/height grid)) -1 -1)]
    (doseq [x (range (grid2d/width grid))]
      (print-cell (grid [x y])))
    (println)))
