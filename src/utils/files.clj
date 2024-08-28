(ns utils.files
  (:require [gdx.files :as files]
            [gdx.files.file-handle :as file-handle]))

(defn recursively-search [folder extensions]
  (loop [[file & remaining] (file-handle/list (files/internal folder))
         result []]
    (cond (nil? file)
          result

          (file-handle/directory? file)
          (recur (concat remaining (file-handle/list file)) result)

          (extensions (file-handle/extension file))
          (recur remaining (conj result (file-handle/path file)))

          :else
          (recur remaining result))))
