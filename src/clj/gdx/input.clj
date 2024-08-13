(ns clj.gdx.input
  (:import com.badlogic.gdx.Gdx))

(defn set-processor! [processor]
  (.setInputProcessor Gdx/input processor))
