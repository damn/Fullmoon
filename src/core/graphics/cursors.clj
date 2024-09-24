(ns core.graphics.cursors
  (:require [core.utils.core :as utils :refer [mapvals]]
            [core.component :as component :refer [defcomponent]])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Pixmap))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (.internal Gdx/files file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (.dispose pixmap)
    cursor))

(defn ^:no-doc ->build [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(defn set-cursor! [{g :context/graphics} cursor-key]
  (.setCursor Gdx/graphics (utils/safe-get (:cursors g) cursor-key)))

(defcomponent :tx/cursor
  (component/do! [[_ cursor-key] ctx]
    (set-cursor! ctx cursor-key)
    ctx))
