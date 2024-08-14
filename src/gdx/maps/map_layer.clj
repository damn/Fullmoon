(ns gdx.maps.map-layer
  (:import com.badlogic.gdx.maps.MapLayer))

(defn visible? [^MapLayer layer]
  (.isVisible layer))
