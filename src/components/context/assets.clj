(ns components.context.assets
  (:require [clojure.string :as str]
            [gdx.assets :as assets]
            [gdx.assets.manager :as manager]
            [gdx.audio.sound :as sound]
            [core.component :refer [defcomponent] :as component]
            core.context)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.files.FileHandle))

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

(defn- load-assets! [manager files ^Class class log?]
  (doseq [file files]
    (when log?
      (println "load-assets" (str "[" (.getSimpleName class) "] - [" file "]")))
    (manager/load manager file class)))

(defn- asset-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (recursively-search folder file-extensions)))

(def ^:private this :context/assets)

(defcomponent this
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
