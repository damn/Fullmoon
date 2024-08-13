(ns gdx.files.file-handle
  (:import com.badlogic.gdx.files.FileHandle))

(defn list       [^FileHandle file] (.list        file))
(defn directory? [^FileHandle file] (.isDirectory file))
(defn extension  [^FileHandle file] (.extension   file))
(defn path       [^FileHandle file] (.path        file))
