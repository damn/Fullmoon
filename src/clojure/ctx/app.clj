(in-ns 'clojure.ctx)

(def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (->mk [_ _ctx]
    (get configs tag)))

(defn- create-into
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

(defn- ->app-listener [ctx]
  (reify AppListener
    (on-create [_]
      (->> ctx
           ; screens require vis-ui / properties (map-editor, property editor uses properties)
           (sort-by (fn [[k _]] (if (= k :context/screens) 1 0)))
           (create-into ctx)
           set-first-screen
           (reset! app-state)))

    (on-dispose [_]
      (run! destroy! @app-state))

    (on-render [_]
      (clear-screen!)
      (screen-render! (current-screen @app-state)))

    (on-resize [_ dim]
      ; TODO fix mac screen resize bug again
      (update-viewports! @app-state dim))))

(defrecord Context [])

(defn start-app!
  "Validates all properties, then creates the context record and starts a libgdx application with the desktop (lwjgl3) backend.
Sets [[app-state]] atom to the context."
  [properties-edn-file]
  (let [ctx (map->Context (->ctx-properties properties-edn-file))
        app (build-property ctx :app/core)]
    (->lwjgl3-app (->app-listener (safe-merge ctx (:app/context app)))
                  (:app/lwjgl3 app))))

(def-attributes
  :fps          :nat-int
  :full-screen? :boolean
  :width        :nat-int
  :height       :nat-int
  :title        :string
  :app/lwjgl3 [:map [:fps
                     :full-screen?
                     :width
                     :height
                     :title]]
  :app/context [:map [:context/assets
                      :context/config
                      :context/graphics
                      :context/screens
                      :context/vis-ui
                      :context/tiled-map-renderer]])

(def-type :properties/app
  {:schema [:app/lwjgl3
            :app/context]
   :overview {:title "Apps" ; - only 1 ? - no overview - ?
              :columns 10}})
