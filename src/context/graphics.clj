(ns context.graphics
  (:require [clj.gdx.graphics.g2d :as g2d]
            [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            (context.graphics cursors
                              image
                              shape-drawer
                              text
                              views)))

; cannot load batch, shape-drawer, gui/world-view via component/load! because no namespaced keys
; could add the namespace 'graphics' manually
; but then we need separate namespaces gui-view & world-view, batch, shape-drawer-texture not disposed.
; but as batch, shape-drawer & gui-view is required for everything to work we can hide them as well.
(defcomponent :context/graphics {}
  (component/create [[_ {:keys [world-view default-font cursors]}] _ctx]
    (let [batch (g2d/->sprite-batch)]
      (g/map->Graphics
       (merge {:batch batch}
              (context.graphics.shape-drawer/->build batch)
              (context.graphics.text/->build default-font)
              (context.graphics.views/->build world-view)
              (context.graphics.cursors/->build cursors)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font cursors]}] _ctx]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))
