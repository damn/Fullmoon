(ns gdx.audio.sound
  (:import com.badlogic.gdx.audio.Sound))

(defn play [sound]
  (.Sound/play sound))
