(in-ns 'clojure.ctx)

(defcomponent :context/screens
  {:data :some
   :let screen-ks}
  (->mk [_ ctx]
    {:screens (create-vs (zipmap screen-ks (repeat nil)) ctx)
     :first-screen (first screen-ks)})

  (destroy! [_]
    ; TODO screens not disposed https://github.com/damn/core/issues/41
    ))

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (->mk [_ _ctx]
    (get configs tag)))

(defcomponent ctx-assets
  {:data :some
   :let {:keys [folder sound-file-extensions image-file-extensions]}}
  (->mk [_ _ctx]
    (let [manager (->asset-manager)
          sound-files   (search-files folder sound-file-extensions)
          texture-files (search-files folder image-file-extensions)]
      (load-assets! manager sound-files   :sound)
      (load-assets! manager texture-files :texture)
      (finish-loading! manager)
      {:manager manager
       :sound-files sound-files
       :texture-files texture-files})))

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (->mk [_ _ctx]
    (map->Graphics
     (let [batch (->sprite-batch)]
       (merge {:batch batch}
              (->shape-drawer batch)
              (->default-font default-font)
              (->views views)
              (->cursors cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose! batch)
    (dispose! shape-drawer-texture)
    (dispose! default-font)
    (run! dispose! (vals cursors))))

(defcomponent ctx-time
  (->mk [_ _ctx]
    {:elapsed 0
     :logic-frame 0}))

(defcomponent :context/vis-ui
  {:data [:enum [:skin-scale/x1 :skin-scale/x2]]
   :let skin-scale}
  (->mk [_ _ctx] (load-ui! skin-scale) :loaded)
  (destroy! [_] (dispose-ui!)))

(defcomponent context-ecs
  (->mk [_ _ctx]
    {}))
