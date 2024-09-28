(in-ns 'clojure.ctx)

(defn- set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defn create-into
  "For every component `[k v]`  `(->mk [k v] ctx)` is non-nil
  or false, assoc's at ctx k v"
  [ctx components]
  (assert (map? ctx))
  (reduce (fn [ctx [k v]]
            (if-let [v (->mk [k v] ctx)]
              (assoc ctx k v)
              ctx))
          ctx
          components))

(defn- ->application-listener [context]
  (proxy [com.badlogic.gdx.ApplicationAdapter] []
    (create []
      (->> context
           ; screens require vis-ui / properties (map-editor, property editor uses properties)
           (sort-by (fn [[k _]] (if (= k :context/screens) 1 0)))
           (create-into context)
           set-first-screen
           (reset! app-state)))

    (dispose []
      (run! destroy! @app-state))

    (render []
      (ScreenUtils/clear Color/BLACK)
      (screen-render! (current-screen @app-state)))

    (resize [w h]
      ; TODO fix mac screen resize bug again
      (on-resize @app-state w h))))

(defrecord Context [])

(defn start-app!
  "Validates all properties, then creates the context record and starts a libgdx application with the desktop (lwjgl3) backend.
Sets [[app-state]] atom to the context."
  [properties-edn-file]
  (let [ctx (map->Context (->ctx-properties properties-edn-file))
        app (build-property ctx :app/core)]
    (->lwjgl3-app (->application-listener (safe-merge ctx (:app/context app)))
                  (:app/lwjgl3 app))))
