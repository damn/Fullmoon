(ns graphics.cursors
  (:require [utils.core :as utils :refer [mapvals]]
            [api.disposable :refer [dispose]])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Pixmap))

(defn- ->cursor [file hotspot-x hotspot-y]
  (let [pixmap (Pixmap. (.internal Gdx/files file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (dispose pixmap)
    cursor))

(defn ->build [cursors]
  {:cursors (mapvals (fn [[file x y]]
                       (->cursor (str "cursors/" file ".png") x y))
                     cursors)})

(extend-type api.context.Context
  api.context/Cursors
  (set-cursor! [{g :context/graphics} cursor-key]
    (.setCursor Gdx/graphics (utils/safe-get (:cursors g) cursor-key))))
