(ns app.start
  (:require [clj.gdx :as gdx]
            [clj.gdx.backends.lwjgl3 :as lwjgl3]
            [core.component :as component]
            [api.context :as ctx]
            api.disposable
            [api.screen :as screen]
            [context.screens :as screens]
            [context.graphics.views :as views]
            [app.state :refer [current-context]]))

(defn- ->context [context]
  (component/load! context)
  (component/build (ctx/->Context) component/create context :log? false))

(defn- ->application [context]
  (gdx/->application-listener
   :create (fn []
             (->> context
                  ->context
                  screens/init-first-screen
                  (reset! current-context)))

   :dispose (fn []
              (component/run-system! component/destroy @current-context))

   :render (fn []
             (views/fix-viewport-update @current-context)
             ;(screen-utils/clear :color/black)
             (-> @current-context
                 ctx/current-screen
                 screen/render!))

   :resize (fn [w h]
             (views/update-viewports @current-context w h))))

(defn start [config]
  (assert (:context config))
  (lwjgl3/->application (->application (:context config))
                        (lwjgl3/->configuration (:app config))))

(extend-type com.badlogic.gdx.utils.Disposable
  api.disposable/Disposable
  (dispose [this]
    (.dispose this)))
