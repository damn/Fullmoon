(ns clojure.gdx
  "API for [libgdx](https://libgdx.com/)"
  (:require [clojure.string :as str])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Color))

(defn exit-app!
  "Schedule an exit from the application. On android, this will cause a call to pause() and dispose() some time in the future, it will not immediately finish your application. On iOS this should be avoided in production as it breaks Apples guidelines

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#exit())"
  []
  (.exit Gdx/app))

(defmacro post-runnable!
  "Posts a Runnable on the main loop thread. In a multi-window application, the Gdx.graphics and Gdx.input values may be unpredictable at the time the Runnable is executed. If graphics or input are needed, they can be copied to a variable to be used in the Runnable. For example:

  final Graphics graphics = Gdx.graphics;

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#postRunnable(java.lang.Runnable))"
  [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn delta-time
  "the time span between the current frame and the last frame in seconds.

  `returns float`

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getDeltaTime())"
  []
  (.getDeltaTime Gdx/graphics))

(defn frames-per-second
  "the average number of frames per second

  `returns int`

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getFramesPerSecond())"
  []
  (.getFramesPerSecond Gdx/graphics))

(defn ->color
  "[javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/graphics/Color.html#%3Cinit%3E(float,float,float,float))"
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defn- ->gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def ^:private ->gdx-input-button (partial ->gdx-field "Input$Buttons"))
(def ^:private ->gdx-input-key    (partial ->gdx-field "Input$Keys"))

(comment
 (and (= (->gdx-input-button :left) 0)
      (= (->gdx-input-button :forward) 4)
      (= (->gdx-input-key :shift-left) 59))
 )

; missing button-pressed?
; also not explaining just-pressed or pressed docs ...
; always link the java class (for all stuff?)
; https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Input.html#isButtonPressed(int)

(defn button-just-pressed?
  ":left, :right, :middle, :back or :forward."
  [b]
  (.isButtonJustPressed Gdx/input (->gdx-input-button b)))

(defn key-just-pressed?
  "See [[key-pressed?]]."
  [k]
  (.isKeyJustPressed Gdx/input (->gdx-input-key k)))

(defn key-pressed?
  "For options see [libgdx Input$Keys docs](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/Input.Keys.html).
  Keys are upper-cased and dashes replaced by underscores.
  For example Input$Keys/ALT_LEFT can be used with :alt-left.
  Numbers via :num-3, etc."
  [k]
  (.isKeyPressed Gdx/input (->gdx-input-key k)))

(defn input-x [] (.getX Gdx/input))
(defn input-y [] (.getY Gdx/input))

(defn set-input-processor! [processor]
  (.setInputProcessor Gdx/input processor))

(defn internal-file
  "Path relative to the asset directory on Android and to the application's root directory on the desktop. On the desktop, if the file is not found, then the classpath is checked. This enables files to be found when using JWS or applets. Internal files are always readonly."
  [path]
  (.internal Gdx/files path))

(defn ->cursor [pixmap [hotspot-x hotspot-y]]
  (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y))

(defn set-cursor! [cursor]
  (.setCursor Gdx/graphics cursor))
