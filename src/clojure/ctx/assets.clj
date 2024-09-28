(in-ns 'clojure.ctx)

(def ctx-assets :context/assets)

(defn- get-asset [ctx file]
  (get (:manager (ctx-assets ctx)) file))

(defn play-sound!
  "Sound is already loaded from file, this will perform only a lookup for the sound and play it.
Returns ctx."
  [ctx file]
  (play! (get-asset ctx file))
  ctx)

(defn texture "Is already cached and loaded." [ctx file]
  (get-asset ctx file))
