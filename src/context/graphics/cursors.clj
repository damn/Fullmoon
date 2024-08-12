(ns context.graphics.cursors
  (:require [clj.gdx.files :as files]
            [clj.gdx.graphics :as graphics]
            [utils.core :as utils :refer [mapvals]]
            [api.disposable :refer [dispose]]))

(defn- ->cursor [file hotspot]
  (let [pixmap (graphics/->pixmap (files/internal file))
        cursor (graphics/->cursor pixmap hotspot)]
    (dispose pixmap)
    cursor))

(defn ->build [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(extend-type api.context.Context
  api.context/Cursors
  (set-cursor! [{g :context/graphics} cursor-key]
    (graphics/set-cursor (utils/safe-get (:cursors g) cursor-key))))
