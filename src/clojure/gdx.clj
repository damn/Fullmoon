(ns clojure.gdx
  (:import (com.badlogic.gdx Gdx)))

(defn exit-app!
  "Schedule an exit from the application. On android, this will cause a call to pause() and dispose() some time in the future, it will not immediately finish your application. On iOS this should be avoided in production as it breaks Apples guidelines

  https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#exit()"
  []
  (.exit Gdx/app))

(defmacro post-runnable!
  "Posts a Runnable on the main loop thread. In a multi-window application, the Gdx.graphics and Gdx.input values may be unpredictable at the time the Runnable is executed. If graphics or input are needed, they can be copied to a variable to be used in the Runnable. For example:

  final Graphics graphics = Gdx.graphics;

  https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#postRunnable(java.lang.Runnable)"
  [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn delta-time
  "the time span between the current frame and the last frame in seconds.

  https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getDeltaTime()
  `returns float`"
  []
  (.getDeltaTime Gdx/graphics))

(defn frames-per-second
  "the average number of frames per second

  https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getFramesPerSecond()
  `returns int`"
  []
  (.getFramesPerSecond Gdx/graphics))
