(ns context.libgdx.graphics
  (:require [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            api.graphics.color
            [app.libgdx.utils.reflect :refer [bind-roots]]
            ; loaded as component
            context.libgdx.graphics.shape-drawer
            context.libgdx.graphics.text-drawer
            context.libgdx.graphics.views
            ; loaded just extend graphics, no component data.
            context.libgdx.graphics.image-drawer
            context.libgdx.graphics.tiled-map-drawer) ; TODO move to tiled ....
  (:import com.badlogic.gdx.Gdx
           (com.badlogic.gdx.graphics Color Pixmap)
           com.badlogic.gdx.graphics.g2d.SpriteBatch))

(bind-roots "com.badlogic.gdx.graphics.Color"
            'com.badlogic.gdx.graphics.Color
            "api.graphics.color")

(defcomponent :context.libgdx/graphics {}
  (component/create [[_ {:keys [tile-size default-font]}] ctx]
    (let [batch (SpriteBatch.)]
      (g/map->Graphics
       (merge {:batch batch}
              ; TODO use shape-drawer/->build
              (context.libgdx.graphics.shape-drawer/->shape-drawer batch)
              (context.libgdx.graphics.text-drawer/->build ctx default-font)
              (context.libgdx.graphics.views/->views tile-size)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font]}] _ctx]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)))

(defn- this [ctx] (:context.libgdx/graphics ctx))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (->cursor [_ file hotspot-x hotspot-y]
    (.newCursor Gdx/graphics
                (Pixmap. (.internal Gdx/files file)) ; TODO pixmap is not disposed ....
                hotspot-x
                hotspot-y))

  (set-cursor! [_ cursor]
    (.setCursor Gdx/graphics cursor))

  (gui-mouse-position    [ctx] (g/gui-mouse-position    (this ctx)))
  (world-mouse-position  [ctx] (g/world-mouse-position  (this ctx)))

  (gui-viewport-width    [ctx] (:gui-viewport-width    (this ctx)))
  (gui-viewport-height   [ctx] (:gui-viewport-height   (this ctx)))

  (world-camera          [ctx] (:world-camera          (this ctx)))
  (world-viewport-width  [ctx] (:world-viewport-width  (this ctx)))
  (world-viewport-height [ctx] (:world-viewport-height (this ctx)))

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a))))
