(ns ^:no-doc core.ctx.graphics
  (:require [core.ctx :refer :all]
            [core.graphics :as g]
            (core.graphics cursors
                           shape-drawer
                           text
                           [views :as views]))
  (:import com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.graphics.g2d Batch SpriteBatch)
           com.badlogic.gdx.utils.Disposable
           com.badlogic.gdx.utils.viewport.Viewport))

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
    (core.graphics/map->Graphics
     (let [batch (SpriteBatch.)]
       (merge {:batch batch}
              (core.graphics.shape-drawer/->build batch)
              (core.graphics.text/->build default-font)
              (core.graphics.views/->build views)
              (core.graphics.cursors/->build cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (.dispose ^Disposable batch)
    (.dispose ^Disposable shape-drawer-texture)
    (.dispose ^Disposable default-font)
    (run! Disposable/.dispose (vals cursors))))

(defn- render-view [{{:keys [^Batch batch] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (g/with-shape-line-width g
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


