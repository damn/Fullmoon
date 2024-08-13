(ns clj.gdx.files.file-handle
  (:import com.badlogic.gdx.files.FileHandle))

(defn list       [file] (.list        file))
(defn directory? [file] (.isDirectory file))
(defn extension  [file] (.extension   file))
(defn path       [file] (.path        file))
