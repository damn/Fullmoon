(ns world.render
  (:require [clojure.gdx.graphics :as g :refer [white black]]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.math.shape :as shape]
            [utils.core :refer [->tile ]]
            [world.core :as world]
            [world.entity :as entity]
            [world.grid :as grid :refer [world-grid]]
            [world.potential-fields :as potential-fields]))

(defn- geom-test []
  (let [position (g/world-mouse-position)
        grid world-grid
        radius 0.8
        circle {:position position :radius radius}]
    (g/draw-circle position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%) (grid/circle->cells grid circle))]
      (g/draw-rectangle x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (shape/circle->outer-rectangle circle)]
      (g/draw-rectangle x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug []
  (let [grid world-grid
        🎥 (g/world-camera)
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
            (let [ratio (/ distance (potential-fields/factions-iterations faction))]
              (g/draw-filled-rectangle x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile []
  (when highlight-blocked-cell?
    (let [[x y] (->tile (g/world-mouse-position))
          cell (get world-grid [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (g/draw-rectangle x y 1 1
                          (case (:movement @cell)
                            :air  [1 1 0 0.5]
                            :none [1 0 0 0.5]))))))

(defn- before-entities [] (tile-debug))

(defn- after-entities []
  #_(geom-test)
  (highlight-mouseover-tile))

(def ^:private explored-tile-color (g/->color 0.5 0.5 0.5 1))

(def ^:private ^:dbg-flag see-all-tiles? false)

(comment
 (def ^:private count-rays? false)

 (def ray-positions (atom []))
 (def do-once (atom true))

 (count @ray-positions)
 2256
 (count (distinct @ray-positions))
 608
 (* 608 4)
 2432
 )

(defn- ->tile-color-setter [light-cache light-position]
  (fn tile-color-setter [_color x y]
    (let [position [(int x) (int y)]
          explored? (get @world/explored-tile-corners position) ; TODO needs int call ?
          base-color (if explored? explored-tile-color black)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (world/ray-blocked? light-position position)]
                       (swap! light-cache assoc position blocked?)
                       blocked?)
                     cache-entry)]
      #_(when @do-once
          (swap! ray-positions conj position))
      (if blocked?
        (if see-all-tiles? white base-color)
        (do (when-not explored?
              (swap! world/explored-tile-corners assoc (->tile position) true))
            white)))))

(defn render-map [light-position]
  (g/draw-tiled-map world/tiled-map
                    (->tile-color-setter (atom nil)
                                         light-position))
  #_(reset! do-once false))

(defn render-world! []
  (🎥/set-position! (g/world-camera) (:position @world/player))
  (render-map (🎥/position (g/world-camera)))
  (g/render-world-view! (fn []
                          (before-entities)
                          (entity/render-entities! (map deref (world/active-entities)))
                          (after-entities))))
