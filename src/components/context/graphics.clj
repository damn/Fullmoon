(ns components.context.graphics
  (:require [gdx.graphics.camera :as camera]
            [gdx.graphics.color :as color]
            [gdx.graphics.g2d :as g2d]
            [gdx.graphics.g2d.batch :as batch]
            [gdx.utils.disposable :refer [dispose]]
            [gdx.utils.viewport.viewport :as viewport]
            [core.component :refer [defcomponent] :as component]
            [core.graphics :as g]
            (components.graphics cursors
                                 shape-drawer
                                 text
                                 views)))

(defcomponent :context/graphics
  {:data :some
   :let {:keys [views default-font cursors]}}
  (component/create [_ _ctx]
    (core.graphics/map->Graphics
     (let [batch (g2d/->sprite-batch)]
       (merge {:batch batch}
              (components.graphics.shape-drawer/->build batch)
              (components.graphics.text/->build default-font)
              (components.graphics.views/->build views)
              (components.graphics.cursors/->build cursors)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(defn- render-view [{{:keys [batch shape-drawer] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [viewport unit-scale]} (view-key g)]
    (batch/set-color batch color/white) ; fix scene2d.ui.tooltip flickering
    (batch/set-projection-matrix batch (camera/combined (viewport/camera viewport)))
    (batch/begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (batch/end batch)))

(extend-type core.context.Context
  core.context/Graphics
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn)))
