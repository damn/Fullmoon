(ns context.graphics
  (:require [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            graphics.shape-drawer
            graphics.text
            graphics.views
            graphics.tiled-map-drawer
            graphics.image)
  (:import com.badlogic.gdx.Gdx
           (com.badlogic.gdx.graphics Color Pixmap)
           com.badlogic.gdx.graphics.g2d.SpriteBatch))

(defcomponent :context/graphics {}
  (component/create [[_ {:keys [world-view default-font]}] ctx]
    (let [batch (SpriteBatch.)]
      (g/map->Graphics
       (merge {:batch batch}
              (graphics.shape-drawer/->build batch)
              (graphics.text/->build ctx default-font)
              (graphics.views/->build world-view)
              (graphics.tiled-map-drawer/->build)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font]}] _ctx]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)))

(defn- this [ctx] (:context/graphics ctx))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (->cursor [_ file hotspot-x hotspot-y]
    (let [pixmap (Pixmap. (.internal Gdx/files file))
          cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
      (dispose pixmap)
      cursor))

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
