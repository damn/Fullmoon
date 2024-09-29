(in-ns 'core.app)

(defn- start-game-fn [world-id]
  (fn [ctx]
    (-> ctx
        (change-screen :screens/world)
        (add-world-ctx world-id))))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (all-properties ctx :properties/worlds)]
                                     [(->text-button (str "Start " id) (start-game-fn id))])
                                   [(when (safe-get config :map-editor?)
                                      [(->text-button "Map editor" #(change-screen % :screens/map-editor))])
                                    (when (safe-get config :property-editor?)
                                      [(->text-button "Property editor" #(change-screen % :screens/property-editor))])
                                    [(->text-button "Exit" (fn [ctx] (exit-app!) ctx))]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))


(defcomponent :main/sub-screen
  (screen-enter [_ ctx]
    (set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(->background-image ctx)
   (->buttons ctx)
   (->actor {:act (fn [_ctx]
                    (when (key-just-pressed? :keys/escape)
                      (exit-app!)))})])

(derive :screens/main-menu :screens/stage)
(defcomponent :screens/main-menu
  (->mk [[k _] ctx]
    {:sub-screen [:main/sub-screen]
     :stage (->stage ctx (->actors ctx))}))
