(in-ns 'world.core)

(defn- geom-test []
  (let [position (g/world-mouse-position)
        radius 0.8
        circle {:position position :radius radius}]
    (g/draw-circle position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%) (circle->cells circle))]
      (g/draw-rectangle x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (shape/circle->outer-rectangle circle)]
      (g/draw-rectangle x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug []
  (let [🎥 (g/world-camera)
        [left-x right-x bottom-y top-y] (🎥/frustum 🎥)]

    (when tile-grid?
      (g/draw-grid (int left-x) (int bottom-y)
                   (inc (int (g/world-viewport-width)))
                   (+ 2 (int (g/world-viewport-height)))
                   1 1 [1 1 1 0.8]))

    (doseq [[x y] (🎥/visible-tiles 🎥)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (g/draw-filled-rectangle x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (g/draw-filled-rectangle x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (factions-iterations faction))]
              (g/draw-filled-rectangle x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

;;

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile []
  (when highlight-blocked-cell?
    (let [[x y] (->tile (g/world-mouse-position))
          cell (get grid [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (g/draw-rectangle x y 1 1
                          (case (:movement @cell)
                            :air  [1 1 0 0.5]
                            :none [1 0 0 0.5]))))))

(defn- render-before-entities [] (tile-debug))

(defn- render-after-entities []
  #_(geom-test)
  (highlight-mouseover-tile))
