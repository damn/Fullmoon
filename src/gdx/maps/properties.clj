(ns gdx.maps.properties
  (:refer-clojure :exclude [get])
  (:import com.badlogic.gdx.maps.MapProperties))

; TODO don't see the point of that
; => proxy lookup or something ... ?
(defn get [^MapProperties properties key]
  (.get properties key))
