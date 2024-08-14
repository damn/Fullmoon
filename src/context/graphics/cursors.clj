(ns context.graphics.cursors
  (:require [gdx.files :as files]
            [gdx.graphics :as graphics]
            [gdx.utils.disposable :refer [dispose]]
            [utils.core :as utils :refer [mapvals]]
            [api.context :as ctx]
            [api.tx :refer [transact!]]))

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

(defmethod transact! :tx.context.cursor/set [[_ cursor-key] ctx]
  (ctx/set-cursor! ctx cursor-key)
  ctx)
