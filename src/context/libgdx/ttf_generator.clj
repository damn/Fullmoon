(ns context.libgdx.ttf-generator ; TODO move to graphics ... or text drawer ... or just a function
  "Convinience clojure constructor for the java
  com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator class."
  (:require api.context)
  (:import com.badlogic.gdx.Gdx
           [com.badlogic.gdx.graphics Texture$TextureFilter]
           ; TODO unused BitmapFont? for dispose ?
           ;[com.badlogic.gdx.graphics.g2d BitmapFont]
           [com.badlogic.gdx.graphics.g2d.freetype
            FreeTypeFontGenerator
            FreeTypeFontGenerator$FreeTypeFontParameter]))

(def ^:private quality-scaling 2)

(defn- ->params [size]
  (let [params (FreeTypeFontGenerator$FreeTypeFontParameter.)]
    (set! (.size params) (* size quality-scaling))
    ; .color and this:
    ;(set! (.borderWidth parameter) 1)
    ;(set! (.borderColor parameter) red)
    (set! (.minFilter params) Texture$TextureFilter/Linear) ; because scaling to world-units
    (set! (.magFilter params) Texture$TextureFilter/Linear)
    params))

(extend-type api.context.Context
  api.context/TrueTypeFontGenerator
  (generate-ttf [_ {:keys [file size]}]
    (let [generator (FreeTypeFontGenerator. (.internal Gdx/files file))
          font (.generateFont generator (->params size))]
      (.dispose generator)
      (.setScale (.getData font) (float (/ quality-scaling)))
      (set! (.markupEnabled (.getData font)) true)
      (.setUseIntegerPositions font false) ; otherwise scaling to world-units (/ 1 48)px not visible
      font)))
