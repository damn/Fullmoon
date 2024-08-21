(ns gdx.files.file-handle
  (:refer-clojure :exclude [list])
  (:import com.badlogic.gdx.files.FileHandle))

(defn list       [^FileHandle file] (.list        file))
(defn directory? [^FileHandle file] (.isDirectory file))
(defn extension  [^FileHandle file] (.extension   file))
(defn path       [^FileHandle file] (.path        file))
