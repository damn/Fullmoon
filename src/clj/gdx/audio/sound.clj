(ns clj.gdx.audio.sound ; TODO sound ....
  (:import com.badlogic.gdx.audio.Sound))

(defn play [sound]
  (.Sound/play sound))
