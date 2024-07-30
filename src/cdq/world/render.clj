(ns cdq.world.render
  (:require [gdl.graphics :as g]
            [gdl.graphics.color :as color]
            [gdl.math.raycaster :as raycaster]
            [utils.core :refer [->tile]])
  (:import com.badlogic.gdx.graphics.Color))

(def ^:private explored-tile-color
  (Color. (float 0.5) (float 0.5) (float 0.5) (float 1)))

(def ^:private light-cache (atom nil))
(declare ^:private map-render-data)

(defn- set-map-render-data! [{:keys [cell-blocked-boolean-array
                                     width
                                     height
                                     explored-tile-corners]}
                             light-position]
  (reset! light-cache {})
  (.bindRoot #'map-render-data [light-position
                                cell-blocked-boolean-array
                                width
                                height
                                explored-tile-corners]))

(def ^:private see-all-tiles? false)

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

(defn- tile-color-setter [_ x y]
  (let [[light-position
         cell-blocked-boolean-array
         width
         height
         explored-tile-corners] map-render-data
        position [(int x) (int y)]
        explored? (get @explored-tile-corners position) ; TODO needs int call ?
        base-color (if explored? explored-tile-color color/black)
        cache-entry (get @light-cache position :not-found)
        blocked? (if (= cache-entry :not-found)
                   (let [blocked? (raycaster/ray-blocked? cell-blocked-boolean-array
                                                          width
                                                          height
                                                          light-position
                                                          position)]
                     (swap! light-cache assoc position blocked?)
                     blocked?)
                   cache-entry)]
    #_(when @do-once
        (swap! ray-positions conj position))
    (if blocked?
      (if see-all-tiles? color/white base-color)
      (do (when-not explored?
            (swap! explored-tile-corners assoc (->tile position) true))
          color/white))))

(defn render-map [{:keys [context/world] g :gdl.libgdx.context/graphics}
                  light-position]
  (set-map-render-data! world light-position)
  (g/render-tiled-map g (:tiled-map world) tile-color-setter)
  #_(reset! do-once false))
