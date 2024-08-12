(ns clj.gdx.input)

(defn set-processor [processor]
  (.setInputProcessor Gdx/input processor))
