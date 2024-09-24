(ns core.graphics
  (:require [core.utils.core :as utils :refer [mapvals]]
            [core.ctx :refer :all]
            (core.graphics shape-drawer
                           text
                           [views :as views]))
  (:import (com.badlogic.gdx.graphics Color Pixmap)
           (com.badlogic.gdx.graphics.g2d Batch SpriteBatch)
           com.badlogic.gdx.utils.viewport.Viewport))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (.internal gdx-files file))
        cursor (.newCursor gdx-graphics pixmap hotspot-x hotspot-y)]
    (.dispose pixmap)
    cursor))

(defn- ->cursors [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(defn set-cursor! [{g :context/graphics} cursor-key]
  (.setCursor gdx-graphics (utils/safe-get (:cursors g) cursor-key)))

(defcomponent :tx/cursor
  (do! [[_ cursor-key] ctx]
    (set-cursor! ctx cursor-key)
    ctx))

(def-attributes
  :views [:map [:gui-view :world-view]]
  :gui-view [:map [:world-width :world-height]]
  :world-view [:map [:tile-size :world-width :world-height]]
  :world-width :pos-int
  :world-height :pos-int
  :tile-size :pos-int
  :default-font [:map [:file :quality-scaling :size]]
  :file :string
  :quality-scaling :pos-int
  :size :pos-int
  :cursors :some)

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (->mk [_ _ctx]
    (map->Graphics
     (let [batch (SpriteBatch.)]
       (merge {:batch batch}
              (core.graphics.shape-drawer/->build batch)
              (core.graphics.text/->build default-font)
              (core.graphics.views/->build views)
              (->cursors cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(defn- render-view [{{:keys [^Batch batch] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (with-shape-line-width g
      unit-scale
      #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

(defn render-gui-view
  "render-fn is a function of param 'g', graphics context."
  [ctx render-fn]
  (render-view ctx :gui-view render-fn))

(extend-type core.ctx.Context
  RenderWorldView
  (render-world-view [ctx render-fn]
    (render-view ctx :world-view render-fn)) )

(defn on-resize [{g :context/graphics} w h]
  (.update (views/gui-viewport g) w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update (views/world-viewport g) w h false))
