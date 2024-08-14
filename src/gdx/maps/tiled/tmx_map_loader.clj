(ns gdx.maps.tiled.tmx-map-loader
  (:refer-clojure :exclude [load])
  (:import com.badlogic.gdx.maps.tiled.TmxMapLoader))

(defn ->tmx-map-loader []
  (TmxMapLoader.))

(defn load [^TmxMapLoader tmx-map-loader file]
  (.load tmx-map-loader file))
