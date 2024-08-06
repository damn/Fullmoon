(ns context.graphics
  (:require [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            graphics.cursors
            graphics.image
            graphics.shape-drawer
            graphics.text
            graphics.tiled-map-drawer
            graphics.views)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Color
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureRegion]))

(defcomponent :context/graphics {}
  (component/create [[_ {:keys [world-view default-font]}] ctx]
    (let [batch (SpriteBatch.)]
      (g/map->Graphics
       (merge {:batch batch}
              (graphics.shape-drawer/->build batch)
              (graphics.text/->build ctx default-font)
              (graphics.tiled-map-drawer/->build)
              (graphics.views/->build world-view)
              (graphics.cursors/->build)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font]}] _ctx]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)))

(defn- this [ctx] (:context/graphics ctx))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (set-cursor! [ctx cursor-key]
    (.setCursor Gdx/graphics (utils/safe-get (:cursors (this ctx)) cursor-key)))

  (gui-mouse-position    [ctx] (g/gui-mouse-position    (this ctx)))
  (world-mouse-position  [ctx] (g/world-mouse-position  (this ctx)))

  (gui-viewport-width    [ctx] (:gui-viewport-width    (this ctx)))
  (gui-viewport-height   [ctx] (:gui-viewport-height   (this ctx)))

  (world-camera          [ctx] (:world-camera          (this ctx)))
  (world-viewport-width  [ctx] (:world-viewport-width  (this ctx)))
  (world-viewport-height [ctx] (:world-viewport-height (this ctx)))

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a)))

  (create-image [ctx file]
    (g/->image (this ctx) (TextureRegion. (ctx/cached-texture ctx file))))

  (get-sub-image [ctx {:keys [texture-region]} [x y w h]]
    (g/->image (this ctx) (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

  (spritesheet [ctx file tilew tileh]
    {:image (ctx/create-image ctx file)
     :tilew tilew
     :tileh tileh})

  (get-sprite [ctx {:keys [image tilew tileh]} [x y]]
    (ctx/get-sub-image ctx
                       image
                       [(* x tilew) (* y tileh) tilew tileh])))
