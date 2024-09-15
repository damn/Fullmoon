(ns utils.files
  (:require [gdx.files.file-handle :as file-handle])
  (:import com.badlogic.gdx.Gdx))

(defn recursively-search [folder extensions]
  (loop [[file & remaining] (file-handle/list (.internal Gdx/files file))
         result []]
    (cond (nil? file)
          result

          (file-handle/directory? file)
          (recur (concat remaining (file-handle/list file)) result)

          (extensions (file-handle/extension file))
          (recur remaining (conj result (file-handle/path file)))

          :else
          (recur remaining result))))
