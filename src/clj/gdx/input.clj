(ns clj.gdx.input
  (:import com.badlogic.gdx.Gdx))

; TODO pass keywords and/or buttons to pressed?-fns

(defn set-processor!       [processor] (.setInputProcessor   Gdx/input processor))
(defn x                    []          (.getX                Gdx/input))
(defn y                    []          (.getY                Gdx/input))
(defn button-pressed?      [button]    (.isButtonPressed     Gdx/input button))
(defn button-just-pressed? [button]    (.isButtonJustPressed Gdx/input button))
(defn key-pressed?         [k]         (.isKeyPressed        Gdx/input k))
(defn key-just-pressed?    [k]         (.isKeyJustPressed    Gdx/input k))
