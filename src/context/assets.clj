(ns context.assets
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent] :as component]
            api.context)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.assets.AssetManager
           com.badlogic.gdx.audio.Sound
           com.badlogic.gdx.files.FileHandle
           com.badlogic.gdx.graphics.Texture))

(defn- ->asset-manager ^AssetManager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))

(defn- recursively-search-files [folder extensions]
  (loop [[^FileHandle file & remaining] (.list (.internal Gdx/files folder))
         result []]
    (cond (nil? file) result
          (.isDirectory file) (recur (concat remaining (.list file)) result)
          (extensions (.extension file)) (recur remaining (conj result (str/replace-first (.path file) folder "")))
          :else (recur remaining result))))

(defn- load-assets! [^AssetManager manager files ^Class klass log-load-assets?]
  (doseq [file files]
    (when log-load-assets?
      (println "load-assets" (str "[" (.getSimpleName klass) "] - [" file "]")))
    (.load manager file klass)))

(defn- load-all-assets! [& {:keys [log-load-assets? sound-files texture-files]}]
  (let [manager (->asset-manager)]
    (load-assets! manager sound-files   Sound   log-load-assets?)
    (load-assets! manager texture-files Texture log-load-assets?)
    (.finishLoading manager)
    manager))

(defcomponent :context/assets {}
  (component/create [[_ {:keys [folder
                                sound-file-extensions
                                image-file-extensions
                                log-load-assets?]}] _ctx]
    (let [sound-files   (recursively-search-files folder sound-file-extensions)
          texture-files (recursively-search-files folder image-file-extensions)]
      {:manager (load-all-assets! :log-load-assets? log-load-assets?
                                  :sound-files sound-files
                                  :texture-files texture-files)
       :sound-files sound-files
       :texture-files texture-files})))

(defn- this [ctx] (:context/assets ctx))

(extend-type api.context.Context
  api.context/Assets
  (play-sound! [ctx file]
    (.play ^Sound (get (:manager (this ctx)) file)))

  (cached-texture [ctx file]
    (let [texture  (get (:manager (this ctx)) file)]
      (assert texture)
      texture))

  (all-sound-files   [ctx] (:sound-files   (this ctx)))
  (all-texture-files [ctx] (:texture-files (this ctx))))
