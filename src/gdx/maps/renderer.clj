(ns gdx.maps.renderer
  (:import com.badlogic.gdx.maps.MapRenderer))

(defn set-view! [^MapRenderer map-renderer camera]
  (.setView map-renderer camera))

(defn render [^MapRenderer map-renderer layer-indices]
  (.render map-renderer (int-array layer-indices)))
