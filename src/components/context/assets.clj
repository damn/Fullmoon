(ns components.context.assets
  (:require [clojure.string :as str]
            [gdx.assets :as assets]
            [gdx.assets.manager :as manager]
            [gdx.audio.sound :as sound]
            [gdx.files :as files]
            [gdx.files.file-handle :as file-handle]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]))

(defn- recursively-search-files [folder extensions]
  (loop [[file & remaining] (file-handle/list (files/internal folder))
         result []]
    (cond (nil? file)
          result

          (file-handle/directory? file)
          (recur (concat remaining (file-handle/list file)) result)

          (extensions (file-handle/extension file))
          (recur remaining (conj result (str/replace-first (file-handle/path file) folder "")))

          :else
          (recur remaining result))))

(defn- load-assets! [manager files ^Class class log-load-assets?]
  (doseq [file files]
    (when log-load-assets?
      (println "load-assets" (str "[" (.getSimpleName class) "] - [" file "]")))
    (manager/load manager file class)))

(defn- load-all-assets! [& {:keys [log-load-assets? sound-files texture-files]}]
  (let [manager (assets/->manager)]
    (load-assets! manager sound-files   com.badlogic.gdx.audio.Sound      log-load-assets?)
    (load-assets! manager texture-files com.badlogic.gdx.graphics.Texture log-load-assets?)
    (manager/finish-loading manager)
    manager))

(defcomponent :context/assets
  {:let {:keys [folder
                sound-file-extensions
                image-file-extensions
                log-load-assets?]}}
  (component/create [_ _ctx]
    (let [sound-files   (recursively-search-files folder sound-file-extensions)
          texture-files (recursively-search-files folder image-file-extensions)]
      {:manager (load-all-assets! :log-load-assets? log-load-assets?
                                  :sound-files sound-files
                                  :texture-files texture-files)
       :sound-files sound-files
       :texture-files texture-files})))

(defn- this [ctx] (:context/assets ctx))

(defn- get-file [ctx file]
  (get (:manager (this ctx)) file))

(extend-type core.context.Context
  core.context/Assets
  (play-sound! [ctx file]
    (sound/play (get-file ctx file)))

  (cached-texture [ctx file]
    (get-file ctx file))

  (all-sound-files   [ctx] (:sound-files   (this ctx)))
  (all-texture-files [ctx] (:texture-files (this ctx))))

(defcomponent :tx/sound
  {:let file}
  (component/do! [_ ctx]
    (ctx/play-sound! ctx file)
    ctx))
