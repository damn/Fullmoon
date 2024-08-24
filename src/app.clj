(ns app
  (:require [clojure.edn :as edn]
            [gdx.app :refer [->application-listener]]
            [gdx.backends.lwjgl3 :as lwjgl3]
            [gdx.graphics.color :as color]
            [gdx.utils.screen-utils :as screen-utils]
            [core.component :as component]
            [core.context :as ctx]
            [core.screen :as screen]))

(def state (atom nil))

(defn- ->context [components]
  (component/load! components)
  (component/create-into (assoc (ctx/->Context) :context/state state)
                         components))

(defn- create-context [components]
  (->> components
       ->context
       ctx/init-first-screen
       (reset! state)))

(defn- destroy-context []
  (run! component/destroy @state))

(defn- render-context []
  (ctx/fix-viewport-update @state)
  (screen-utils/clear color/black)
  (-> @state
      ctx/current-screen
      (screen/render! state)))

(defn- update-viewports [w h]
  (ctx/update-viewports @state w h))

(defn- ->application [components]
  (->application-listener
   :create #(create-context components)
   :dispose destroy-context
   :render render-context
   :resize update-viewports))

(defn- start [config]
  (assert (:context config))
  (component/load-ks! (:components config))
  (lwjgl3/->application (->application (:context config))
                        (lwjgl3/->configuration (:app config))))

(defn -main []
  (-> "resources/app.edn"
      slurp
      edn/read-string
      start))
