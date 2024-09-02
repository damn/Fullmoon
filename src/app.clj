(ns app
  (:require [clojure.string :as str]
            [utils.files :as files]
            [utils.core :refer [safe-merge safe-get]]
            [gdx.app :refer [->application-listener]]
            [gdx.backends.lwjgl3 :as lwjgl3]
            [gdx.graphics.color :as color]
            [gdx.utils.screen-utils :as screen-utils]
            [core.component :as component]
            [core.context :as ctx]
            [components.context.properties :as properties]))

(def state (atom nil))

(defn- require-all-components! []
  (doseq [file (files/recursively-search "src/components/" #{"clj"})
          :let [ns (-> file
                       (str/replace "src/" "")
                       (str/replace ".clj" "")
                       (str/replace "/" ".")
                       symbol)]]
    (when-not (find-ns ns)
      (require ns))))

(defn- ->application [context]
  (->application-listener
   :create (fn []
             (require-all-components!)
             (->> context
                  (component/create-into (assoc (ctx/->Context) :context/state state))
                  ctx/init-first-screen
                  (reset! state)))

   :dispose (fn []
              (run! component/destroy @state))

   :render (fn []
             (ctx/fix-viewport-update @state)
             (screen-utils/clear color/black)
             (-> @state
                 ctx/current-screen
                 (component/render! state)))

   :resize (fn [w h]
             (ctx/update-viewports @state w h))))

(defn- inject [components k value]
  (for [component components]
    (if (= (first component) k)
      [k (safe-merge (component 1) value)]
      component)))

(defn -main [& [file]]
  (let [properties (properties/load-raw-properties file)
        {:keys [app/context app/lwjgl3]} (safe-get properties :app/core)
        context (inject context :context/properties {:file file
                                                     :properties properties})]
    (lwjgl3/->application (->application context)
                          (lwjgl3/->configuration lwjgl3))))
