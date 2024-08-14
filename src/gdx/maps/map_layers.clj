(ns gdx.maps.map-layers
  (:refer-clojure :exclude [get])
  (:import com.badlogic.gdx.maps.MapLayers))

; TODO index & remove also takes layer itself ....
; or use kw (! ) ...

(defn index
  "Get the index of the layer having the specified name, or nil if no such layer exists."
  [^MapLayers layers ^String layer-name]
  (let [idx (.getIndex layers layer-name)]
    (when-not (= idx -1)
      idx)))

(defn get
  "the first layer having the specified name, if one exists, otherwise nil"
  [^MapLayers layers ^String layer-name]
  (.get layers layer-name))

(defn remove! [^MapLayers layers ^Integer layer-index]
  (.remove layers layer-index))
