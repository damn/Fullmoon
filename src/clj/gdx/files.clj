(ns clj.gdx.files
  (:import com.badlogic.gdx.Gdx))

(defn internal [file]
  (.internal Gdx/files file))
