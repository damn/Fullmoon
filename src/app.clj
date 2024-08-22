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

(def state (atom nil))

(defn- ->context [context]
  (component/load! context)
  (component/build (assoc (ctx/->Context) :context/state state)
                   component/create
                   context
                   :log?
                   false))

(defn- create-context [context]
  (->> context
       ->context
       screens/init-first-screen
       (reset! state)))

(defn- destroy-context []
  (component/run-system! component/destroy @state))

(defn- render-context []
  (views/fix-viewport-update @state)
  (screen-utils/clear color/black)
  (-> @state
      ctx/current-screen
      (screen/render! state)))

(defn- update-viewports [w h]
  (views/update-viewports @state w h))

(defn- ->application [context]
  (->application-listener
   :create #(create-context context)
   :dispose destroy-context
   :render render-context
   :resize update-viewports))

(defn- start [config]
  (assert (:context config))
  (component/load! (:components config))
  (lwjgl3/->application (->application (:context config))
                        (lwjgl3/->configuration (:app config))))

(defn -main []
  (-> "resources/app.edn"
      slurp
      edn/read-string
      start))
