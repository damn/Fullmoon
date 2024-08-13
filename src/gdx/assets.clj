(ns gdx.assets
  (:import com.badlogic.gdx.assets.AssetManager))

(defn ->manager ^AssetManager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))
