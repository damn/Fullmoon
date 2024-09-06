(ns components.context.assets
  (:require [clojure.string :as str]
            [gdx.assets :as assets]
            [gdx.assets.manager :as manager]
            [gdx.audio.sound :as sound]
            [utils.files :as files]
            [core.component :refer [defcomponent] :as component]
            core.context))

(defn- load-assets! [manager files ^Class class log?]
  (doseq [file files]
    (when log?
      (println "load-assets" (str "[" (.getSimpleName class) "] - [" file "]")))
    (manager/load manager file class)))

(defn- asset-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (files/recursively-search folder file-extensions)))

(defcomponent :context/assets
  {:data :some
   :let {:keys [folder
                sound-file-extensions
                image-file-extensions
                log?]}}
  (component/create [_ _ctx]
    (let [manager (assets/->manager)
          sound-files   (asset-files folder sound-file-extensions)
          texture-files (asset-files folder image-file-extensions)]
      (load-assets! manager sound-files   com.badlogic.gdx.audio.Sound      log?)
      (load-assets! manager texture-files com.badlogic.gdx.graphics.Texture log?)
      (manager/finish-loading manager)
      {:manager manager
       :sound-files sound-files
       :texture-files texture-files})))

(defn- this [ctx] (:context/assets ctx))

(defn- get-asset [ctx file]
  (get (:manager (this ctx)) file))

(extend-type core.context.Context
  core.context/Assets
  (play-sound! [ctx file]
    (sound/play (get-asset ctx file)))

  (cached-texture [ctx file]
    (get-asset ctx file))

  (all-sound-files   [ctx] (:sound-files   (this ctx)))
  (all-texture-files [ctx] (:texture-files (this ctx))))
