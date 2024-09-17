(ns gdx.scene2d.ui.label
  (:import com.badlogic.gdx.scenes.scene2d.ui.Label))

(defn set-text! [^Label label ^CharSequence text]
  (.setText label text))

; 1. ->label needs to return a 'Label' type hint ?
; widgets no need ctx ...
