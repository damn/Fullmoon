(ns context.graphics
  (:require [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            graphics.cursors
            graphics.shape-drawer
            graphics.text
            graphics.views)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Color
           [com.badlogic.gdx.graphics.g2d SpriteBatch]))

; cannot load batch, shape-drawer, gui/world-view via component/load! because no namespaced keys
; could add the namespace 'graphics' manually
; but then we need separate namespaces gui-view & world-view, batch, shape-drawer-texture not disposed.
; but as batch, shape-drawer & gui-view is required for everything to work we can hide them as well.
(defcomponent :context/graphics {}
  (component/create [[_ {:keys [world-view default-font cursors]}] _ctx]
    (let [batch (SpriteBatch.)]
      (g/map->Graphics
       (merge {:batch batch}
              (graphics.shape-drawer/->build batch)
              (graphics.text/->build default-font)
              (graphics.views/->build world-view)
              (graphics.cursors/->build cursors)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font cursors]}] _ctx]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a))))
