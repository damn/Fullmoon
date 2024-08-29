(ns app
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [utils.files :as files]
            [utils.core :refer [safe-get]]
            [gdx.app :refer [->application-listener]]
            [gdx.backends.lwjgl3 :as lwjgl3]
            [gdx.graphics.color :as color]
            [gdx.utils.screen-utils :as screen-utils]
            [core.component :as component]
            [core.context :as ctx]))

(def state (atom nil))

(defn- require-all-components! []
  (doseq [file (files/recursively-search "src/components/" #{"clj"})
          :let [ns (-> file
                       (str/replace "src/" "")
                       (str/replace ".clj" "")
                       (str/replace "/" ".")
                       symbol)]]
    (when-not (find-ns ns)
      ;(println "require" ns)
      (require ns))))

(defn- ->application [context]
  (->application-listener
   :create (fn []
             (require-all-components!) ; here @ create because files/internal requires libgdx context
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

(defn- load-properties [file]
  (let [properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (->> properties
         (#(zipmap (map :property/id %) %)))))

(defn -main [& [file]]
  (let [properties (load-properties file)
        {:keys [app/context app/lwjgl3]} (safe-get properties :app/core)
        context (merge context
                       {:context/properties {:file file
                                             :properties properties}
                        ; map editor calls ctx/get-property & property editor ctx/all-properties
                        ; so load afterwards
                        :context/screens [:screens/main-menu
                                          :screens/map-editor
                                          ;:screens/minimap
                                          :screens/options-menu
                                          :screens/property-editor
                                          :screens/world]})]
    (lwjgl3/->application (->application context)
                          (lwjgl3/->configuration lwjgl3))))
