(ns context.graphics
  (:require [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            graphics.shape-drawer
            graphics.image
            graphics.text
            graphics.cursors
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

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a)))

  ; to cursors
  (set-cursor! [ctx cursor-key]
    (.setCursor Gdx/graphics (utils/safe-get (:cursors (this ctx)) cursor-key)))

  ; to image
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
