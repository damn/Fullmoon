(ns level.grid
  (:require [data.grid2d :as g]
            [utils.core :refer [assoc-ks]]
            [level.caves :as caves]
            [level.nads :as nads]))

(defn scale-grid [grid [w h]]
  (g/create-grid (* (g/width grid)  w)
                 (* (g/height grid) h)
                 (fn [[x y]]
                   (get grid
                        [(int (/ x w))
                         (int (/ y h))]))))

(defn scalegrid [grid factor]
  (g/create-grid (* (g/width grid) factor)
                 (* (g/height grid) factor)
                 (fn [posi]
                   (get grid (mapv #(int (/ % factor)) posi)))))

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

(defn printgrid ; print-grid in data.grid2d is y-down
  "Prints with y-up coordinates."
  [grid]
  (doseq [y (range (dec (g/height grid)) -1 -1)]
    (doseq [x (range (g/width grid))]
      (print-cell (grid [x y])))
    (println)))

(let [idxvalues-order [[1 0] [-1 0] [0 1] [0 -1]]]
  (assert (= (g/get-4-neighbour-positions [0 0])
             idxvalues-order)))

(comment
  ; Values for every neighbour:
  {          [0 1] 1
   [-1 0]  8          [1 0] 2
             [0 -1] 4 })

; so the idxvalues-order corresponds to the following values for a neighbour tile:
(def ^:private idxvalues [2 8 1 4])

(defn- calculate-index-value [position->transition? idx position]
  (if (position->transition? position)
    (idxvalues idx)
    0))

(defn transition-idx-value [position position->transition?]
  (->> position
       g/get-4-neighbour-positions
       (map-indexed (partial calculate-index-value
                             position->transition?))
       (apply +)))

; TODO generates 51,52. not max 50
(defn cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (caves/generate (java.util.Random.) size size :wide)
        grid (nads/fix grid)]
    (assert (= #{:wall :ground} (set (g/cells grid))))
    {:start start
     :grid grid}))

(defn adjacent-wall-positions [grid]
  (filter (fn [p] (and (= :wall (get grid p))
                       (some #(= :ground (get grid %))
                             (g/get-8-neighbour-positions p))))
          (g/posis grid)))

(defn flood-fill [grid start walk-on-position?]
  (loop [next-positions [start]
         filled []
         grid grid]
    (if (seq next-positions)
      (recur (filter #(and (get grid %)
                           (walk-on-position? %))
                     (distinct
                      (mapcat g/get-8-neighbour-positions
                              next-positions)))
             (concat filled next-positions)
             (assoc-ks grid next-positions nil))
      filled)))

(comment
 (let [{:keys [start grid]} (cave-grid :size 15)
       _ (println "BASE GRID:\n")
       _ (printgrid grid)
       ;_ (println)
       ;_ (println "WITH START POSITION (0) :\n")
       ;_ (printgrid (assoc grid start 0))
       ;_ (println "\nwidth:  " (g/width  grid)
       ;           "height: " (g/height grid)
       ;           "start " start "\n")
       ;_ (println (g/posis grid))
       _ (println "\n\n")
       filled (flood-fill grid start (fn [p] (= :ground (get grid p))))
       _ (printgrid (reduce #(assoc %1 %2 nil) grid filled))])
 )
