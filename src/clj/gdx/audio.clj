(ns clj.gdx.audio
  (:import com.badlogic.gdx.audio.Sound))

(defn play [^Sound sound]
  (.play sound))
