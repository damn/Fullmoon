(ns context.libgdx.graphics.text-drawer
  (:require [clojure.string :as str]
            api.graphics
            context.libgdx.ttf-generator)
  (:import com.badlogic.gdx.graphics.g2d.BitmapFont
           com.badlogic.gdx.utils.Align))

; TODO BitmapFont does not draw world-unit-scale idk how possible, maybe setfontdata something
; (did draw world scale @ test ...)
(defn ->build [default-font]
  {:default-font (or (and default-font
                          (api.context/generate-ttf ctx default-font))
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
