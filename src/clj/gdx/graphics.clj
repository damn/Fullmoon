(ns clj.gdx.graphics
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Pixmap))

(defn width []
  (.getWidth Gdx/graphics))

(defn height []
  (.getHeight Gdx/graphics))

(defn ->cursor [pixmap [hotspot-x hotspot-y]]
  (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y))

(defn set-cursor [cursor]
  (.setCursor Gdx/graphics cursor))

(defn ->pixmap [file]
  (PixMap. file))

(defn ->color [r g b a]
  (Color. (float r) (float g) (float b) (float a)))
