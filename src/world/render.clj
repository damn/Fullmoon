(ns world.render
  (:require [gdx.graphics :as graphics]
            [gdx.graphics.color :as color]
            [utils.core :refer [->tile]]
            [api.context :as ctx]
            [world.raycaster :as raycaster]))

(def ^:private explored-tile-color
  (graphics/->color 0.5 0.5 0.5 1))

(def ^:private light-cache (atom nil))
(declare ^:private map-render-data)

(defn- set-map-render-data! [{:keys [world/raycaster world/explored-tile-corners]}
                             light-position]
  (reset! light-cache {})
  (.bindRoot #'map-render-data [light-position
                                raycaster
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
         raycaster
         explored-tile-corners] map-render-data
        position [(int x) (int y)]
        explored? (get @explored-tile-corners position) ; TODO needs int call ?
        base-color (if explored? explored-tile-color color/black)
        cache-entry (get @light-cache position :not-found)
        blocked? (if (= cache-entry :not-found)
                   (let [blocked? (raycaster/ray-blocked? raycaster light-position position)]
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

(defn render-map [{:keys [world/tiled-map] :as ctx} light-position]
  (set-map-render-data! ctx light-position)
  (ctx/render-tiled-map ctx tiled-map tile-color-setter)
  #_(reset! do-once false))
