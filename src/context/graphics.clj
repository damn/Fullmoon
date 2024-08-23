(ns context.graphics
  (:require [gdx.graphics.camera :as camera]
            [gdx.graphics.color :as color]
            [gdx.graphics.g2d :as g2d]
            [gdx.graphics.g2d.batch :as batch]
            [gdx.utils.disposable :refer [dispose]]
            [gdx.utils.viewport.viewport :as viewport]
            [core.component :refer [defcomponent] :as component]
            [api.graphics :as g]
            (graphics cursors
                      image
                      shape-drawer
                      text
                      views)))

; cannot load batch, shape-drawer, gui/world-view via component/load! because no namespaced keys
; could add the namespace 'graphics' manually
; but then we need separate namespaces gui-view & world-view, batch, shape-drawer-texture not disposed.
; but as batch, shape-drawer & gui-view is required for everything to work we can hide them as well.
(defcomponent :context/graphics {}
  (component/create [[_ {:keys [views default-font cursors]}] _ctx]
    (api.graphics/map->Graphics
     (let [batch (g2d/->sprite-batch)]
       (merge {:batch batch}
              (graphics.shape-drawer/->build batch)
              (graphics.text/->build default-font)
              (graphics.views/->build views)
              (graphics.cursors/->build cursors)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font cursors]}] _ctx]
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

(extend-type api.context.Context
  api.context/Graphics
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn)))
