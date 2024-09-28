(in-ns 'clojure.ctx)

(defcomponent :context/assets
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

(defn- get-asset [ctx file]
  (get (:manager (:context/assets ctx)) file))

(defn texture "Is already cached and loaded." [ctx file]
  (get-asset ctx file))

(defn play-sound!
  "Sound is already loaded from file, this will perform only a lookup for the sound and play it.
Returns ctx."
  [ctx file]
  (play! (get-asset ctx file))
  ctx)

(defcomponent :tx/sound
  {:data :sound}
  (do! [[_ file] ctx]
    (play-sound! ctx file)))
