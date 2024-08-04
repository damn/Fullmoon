(ns graphics.text
  (:require [clojure.string :as str]
            api.graphics)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Texture$TextureFilter
           com.badlogic.gdx.graphics.g2d.BitmapFont
           com.badlogic.gdx.utils.Align
           [com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter]))

(defn- ->params [size quality-scaling]
  (let [params (FreeTypeFontGenerator$FreeTypeFontParameter.)]
    (set! (.size params) (* size quality-scaling))
    ; .color and this:
    ;(set! (.borderWidth parameter) 1)
    ;(set! (.borderColor parameter) red)
    (set! (.minFilter params) Texture$TextureFilter/Linear) ; because scaling to world-units
    (set! (.magFilter params) Texture$TextureFilter/Linear)
    params))

(defn- generate-ttf [{:keys [file size quality-scaling]}]
  (let [generator (FreeTypeFontGenerator. (.internal Gdx/files file))
        font (.generateFont generator (->params size quality-scaling))]
    (.dispose generator)
    (.setScale (.getData font) (float (/ quality-scaling)))
    (set! (.markupEnabled (.getData font)) true)
    (.setUseIntegerPositions font false) ; otherwise scaling to world-units (/ 1 48)px not visible
    font))

(defn ->build [ctx default-font]
  {:default-font (or (and default-font (generate-ttf default-font))
                     (BitmapFont.))})

(defn- text-height [^BitmapFont font text]
  (-> text
      (str/split #"\n")
      count
      (* (.getLineHeight font))))

(extend-type api.graphics.Graphics
  api.graphics/TextDrawer
  (draw-text [{:keys [default-font unit-scale batch]}
              {:keys [x y text font h-align up? scale]}]
    (let [^BitmapFont font (or font default-font)
          data (.getData font)
          old-scale (float (.scaleX data))]
      (.setScale data (* old-scale (float unit-scale) (float (or scale 1))))
      (.draw font
             batch
             (str text)
             (float x)
             (+ (float y) (float (if up? (text-height font text) 0)))
             (float 0) ; target-width
             (case (or h-align :center)
               :center Align/center
               :left   Align/left
               :right  Align/right)
             false) ; wrap false, no need target-width
      (.setScale data old-scale))))
