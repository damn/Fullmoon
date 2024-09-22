(ns core.debug-render
  (:require [utils.core :refer [->tile]]
            [gdx.graphics.camera :as camera]
            [math.geom :as geom]
            [core.world :refer [world-grid]]
            [core.g :as g]
            [core.graphics.views :refer [world-mouse-position world-camera world-viewport-width world-viewport-height]]
            [core.grid :refer [circle->cells]]
            [core.potential-fields :as potential-field]))

(defn- geom-test [g ctx]
  (let [position (world-mouse-position ctx)
        grid (world-grid ctx)
        radius 0.8
        circle {:position position :radius radius}]
    (g/draw-circle g position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%)
                       (circle->cells grid circle))]
      (g/draw-rectangle g x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (geom/circle->outer-rectangle circle)]
      (g/draw-rectangle g x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug [g ctx]
  (let [grid (world-grid ctx)
        world-camera (world-camera ctx)
        [left-x right-x bottom-y top-y] (camera/frustum world-camera)]

    (when tile-grid?
      (g/draw-grid g (int left-x) (int bottom-y)
                   (inc (int (world-viewport-width ctx)))
                   (+ 2 (int (world-viewport-height ctx)))
                   1 1 [1 1 1 0.8]))

    (doseq [[x y] (camera/visible-tiles world-camera)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (g/draw-filled-rectangle g x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (g/draw-filled-rectangle g x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance entity]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (@#'potential-field/factions-iterations faction))]
              (g/draw-filled-rectangle g x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile [g ctx]
  (when highlight-blocked-cell?
    (let [[x y] (->tile (world-mouse-position ctx))
          cell (get (world-grid ctx) [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (g/draw-rectangle g x y 1 1
                          (case (:movement @cell)
                            :air  [1 1 0 0.5]
                            :none [1 0 0 0.5]))))))

(defn before-entities [ctx g]
  (tile-debug g ctx))

(defn after-entities [ctx g]
  #_(geom-test g ctx)
  (highlight-mouseover-tile g ctx))
