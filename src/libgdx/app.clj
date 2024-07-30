(ns libgdx.app
  (:require [core.component :as component]
            [api.context :as ctx]
            [api.graphics.color :as color]
            [context.screens :as screens]
            [context.libgdx.graphics :as graphics]
            [app.state :refer [current-context]])
  (:import (com.badlogic.gdx Gdx ApplicationAdapter)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.utils.ScreenUtils))

(defn ->context [context]
  (component/load! context)
  (component/build (ctx/->Context) ctx/create context :log? false))

(defn- ->application [context]
  (proxy [ApplicationAdapter] []
    (create []
      (->> context
           ->context
           screens/init-first-screen
           (reset! current-context)))

    (dispose []
      (component/run-system! ctx/destroy @current-context))

    (render []
      (ScreenUtils/clear color/black)
      (let [context @current-context]
        (graphics/fix-viewport-update context)
        (component/run-system! ctx/render context)))

    (resize [w h]
      (graphics/update-viewports @current-context w h))))

(defn- lwjgl3-configuration [{:keys [title width height full-screen? fps]}]
  {:pre [title
         width
         height
         (boolean? full-screen?)
         (or (nil? fps) (int? fps))]}
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))

(defn start
  "Required keys:
   {:app {:title \"gdl demo\"
          :width 800
          :height 600
          :full-screen? false}
    :context ...}
  "
  [config]
  (assert (:context config))
  (Lwjgl3Application. (->application (:context config))
                      (lwjgl3-configuration (:app config))))

(extend-type api.context.Context
  api.context/Application
  (exit-app [_]
    (.exit Gdx/app)))

(extend-type com.badlogic.gdx.utils.Disposable
  api.disposable/Disposable
  (dispose [this]
    (.dispose this)))
