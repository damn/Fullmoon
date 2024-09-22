(ns core.render
  (:require [math.raycaster :as raycaster]
            [gdx.graphics :as graphics]
            [utils.core :refer [->tile]]
            [core.ctx.tiled-map-renderer :as tiled-map-renderer])
  (:import com.badlogic.gdx.graphics.Color))

(def ^:private explored-tile-color
  (graphics/->color 0.5 0.5 0.5 1))

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

(defn- ->tile-color-setter [light-cache light-position raycaster explored-tile-corners]
  (fn tile-color-setter [_color x y]
    (let [position [(int x) (int y)]
          explored? (get @explored-tile-corners position) ; TODO needs int call ?
          base-color (if explored? explored-tile-color Color/BLACK)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (raycaster/ray-blocked? raycaster light-position position)]
                       (swap! light-cache assoc position blocked?)
                       blocked?)
                     cache-entry)]
      #_(when @do-once
          (swap! ray-positions conj position))
      (if blocked?
        (if see-all-tiles? Color/WHITE base-color)
        (do (when-not explored?
              (swap! explored-tile-corners assoc (->tile position) true))
            Color/WHITE)))))

(defn render-map [{:keys [context/tiled-map] :as ctx} light-position]
  (tiled-map-renderer/render! ctx
                              tiled-map
                              (->tile-color-setter (atom nil)
                                                   light-position
                                                   (:context/raycaster ctx)
                                                   (:context/explored-tile-corners ctx)))
  #_(reset! do-once false))
