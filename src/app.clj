(ns app
  (:require [clojure.edn :as edn]
            [gdx.app :refer [->application-listener]]
            [gdx.backends.lwjgl3 :as lwjgl3]
            [gdx.graphics.color :as color]
            [gdx.utils.screen-utils :as screen-utils]
            [core.component :as component]
            [api.context :as ctx]
            [api.screen :as screen]
            [context.screens :as screens]
            [context.graphics.views :as views]))

(def current-context (atom nil))

(defn change-screen!
  "change-screen is dangerous, because it swap!s the current-context atom
  and then the screen render might continue with another outdated context.
  So do it always at end of a frame."
  [new-screen-key]
  (swap! current-context ctx/change-screen new-screen-key))

(defn- ->context [context]
  (component/load! context)
  (component/build (ctx/->Context) component/create context :log? false))

(defn- create-context [context]
  (->> context
       ->context
       screens/init-first-screen
       (reset! current-context)))

(defn- destroy-context []
  (component/run-system! component/destroy @current-context))

(defn- render-context []
  (views/fix-viewport-update @current-context)
  (screen-utils/clear color/black)
  (-> @current-context
      ctx/current-screen
      screen/render!))

(defn- update-viewports [w h]
  (views/update-viewports @current-context w h))

(defn- ->application [context]
  (->application-listener
   :create #(create-context context)
   :dispose destroy-context
   :render render-context
   :resize update-viewports))

(defn- start [config]
  (assert (:context config))
  (lwjgl3/->application (->application (:context config))
                        (lwjgl3/->configuration (:app config))))

(defn -main []
  (-> "resources/app.edn"
      slurp
      edn/read-string
      start))
