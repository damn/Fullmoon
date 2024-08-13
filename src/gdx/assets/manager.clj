(ns gdx.assets.manager
  (:refer-clojure :exclude [load])
  (:import com.badlogic.gdx.assets.AssetManager))

(defn load [^AssetManager manager ^String file ^Class class]
  (.load manager file class))

(defn finish-loading [^AssetManager manager]
  (.finishLoading manager))
