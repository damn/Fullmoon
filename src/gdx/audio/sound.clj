(ns gdx.audio.sound
  (:import com.badlogic.gdx.audio.Sound))

(defn play [^Sound sound]
  (.play sound))
