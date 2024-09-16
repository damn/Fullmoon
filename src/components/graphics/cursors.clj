(ns components.graphics.cursors
  (:require [utils.core :as utils :refer [mapvals]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Pixmap))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (.internal Gdx/files file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (.dispose pixmap)
    cursor))

(defn ->build [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(extend-type core.context.Context
  core.context/Cursors
  (set-cursor! [{g :context/graphics} cursor-key]
    (.setCursor Gdx/graphics (utils/safe-get (:cursors g) cursor-key))))

(defcomponent :tx.context.cursor/set
  (component/do! [[_ cursor-key] ctx]
    (ctx/set-cursor! ctx cursor-key)
    ctx))
