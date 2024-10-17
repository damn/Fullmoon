(ns gdx.assets
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as str])
  (:import (com.badlogic.gdx Gdx)
           (com.badlogic.gdx.audio Sound)
           (com.badlogic.gdx.assets AssetManager)
           (com.badlogic.gdx.files FileHandle)
           (com.badlogic.gdx.graphics Texture)))

(defn- recursively-search [folder extensions]
  (loop [[^FileHandle file & remaining] (.list (.internal Gdx/files folder))
         result []]
    (cond (nil? file)
          result

          (.isDirectory file)
          (recur (concat remaining (.list file)) result)

          (extensions (.extension file))
          (recur remaining (conj result (.path file)))

          :else
          (recur remaining result))))

(defn- search-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (recursively-search folder file-extensions)))

(declare ^:private ^AssetManager manager
         ^{:doc "All loaded sound files."} all-sound-files
         ^{:doc "All loaded texture files."} all-texture-files)

(defn- load-all! [files class]
  (doseq [file files]
    (.load manager ^String file ^Class class)))

(defn load!
  "Recursively searches folder for `.wav`, `.png` and `.bmp` files and loads those assets into Sound or Texture."
  [folder]
  (let [manager (AssetManager.)
        sound-files   (search-files folder #{"wav"})
        texture-files (search-files folder #{"png" "bmp"})]
    (.bindRoot #'manager manager)
    (load-all! sound-files   Sound)
    (load-all! texture-files Texture)
    (.finishLoading manager)
    (.bindRoot #'all-sound-files sound-files)
    (.bindRoot #'all-texture-files texture-files)))

(defn dispose!
  "Frees all resources of loaded assets."
  []
  (.dispose manager))

(defn get
  "Gets the asset at file-path."
  [path]
  (.get manager ^String path))

(defn play-sound! [path]
  (Sound/.play (get path)))
