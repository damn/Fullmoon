(ns components.graphics.cursors
  (:require [gdx.files :as files]
            [gdx.graphics :as graphics]
            [gdx.utils.disposable :refer [dispose]]
            [utils.core :as utils :refer [mapvals]]
            [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.effect :as effect]))

(defn- ->cursor [file hotspot]
  (let [pixmap (graphics/->pixmap (files/internal file))
        cursor (graphics/->cursor pixmap hotspot)]
    (dispose pixmap)
    cursor))

(defn ->build [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(extend-type core.context.Context
  core.context/Cursors
  (set-cursor! [{g :context/graphics} cursor-key]
    (graphics/set-cursor (utils/safe-get (:cursors g) cursor-key))))

(defcomponent :tx.context.cursor/set {}
  (effect/do! [[_ cursor-key] ctx]
    (ctx/set-cursor! ctx cursor-key)
    ctx))
