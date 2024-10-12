(ns clojure.gdx.audio
  (:require [clojure.gdx.assets :as assets])
  (:import (com.badlogic.gdx.audio Sound)))

(defn play-sound! [path]
  (Sound/.play (assets/get path)))
