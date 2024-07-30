(ns ^:no-doc gdl.libgdx.context.assets
  (:require [clojure.string :as str]
            [core.component :as component]
            [gdl.context :as ctx])
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

(defn- load-asset! [^AssetManager manager file ^Class klass log-load-assets?]
  (when log-load-assets?
    (println "load-assets" (str "[" (.getSimpleName klass) "] - [" file "]")))
  (.load manager file klass))

(defn- load-all-assets! [& {:keys [log-load-assets? sound-files texture-files]}]
  (let [manager (->asset-manager)]
    (doseq [file sound-files]   (load-asset! manager file Sound   log-load-assets?))
    (doseq [file texture-files] (load-asset! manager file Texture log-load-assets?))
    (.finishLoading manager)
    manager))

(component/def :gdl.libgdx.context/assets {}
  _
  (ctx/create [_ _ctx]
    (let [folder "resources/" ; TODO should be set in classpath and not necessary here ?
          sound-files   (recursively-search-files folder #{"wav"})
          texture-files (recursively-search-files folder #{"png" "bmp"})]
      {:manager (load-all-assets! :log-load-assets? false
                                  :sound-files sound-files
                                  :texture-files texture-files)
       :sound-files sound-files
       :texture-files texture-files})))

(defn- this [ctx] (:gdl.libgdx.context/assets ctx))

(extend-type gdl.context.Context
  gdl.context/SoundStore
  (play-sound! [ctx file]
    (.play ^Sound (get (:manager (this ctx)) file)))

  gdl.context/Assets
  (cached-texture [ctx file]
    (let [texture  (get (:manager (this ctx)) file)]
      (assert texture)
      texture))

  (all-sound-files   [ctx] (:sound-files   (this ctx)))
  (all-texture-files [ctx] (:texture-files (this ctx))))
