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
            graphics.views)
  (:import com.badlogic.gdx.Gdx
           [com.badlogic.gdx.graphics Color OrthographicCamera]
           [com.badlogic.gdx.graphics.g2d SpriteBatch TextureRegion]
           com.badlogic.gdx.utils.viewport.Viewport))

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

(defn- render-view [ctx view-key draw-fn]
  (let [{:keys [^SpriteBatch batch shape-drawer] :as g} (this ctx)
        {:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

(defn- gui-viewport   ^Viewport [ctx] (-> ctx this :gui-view   :viewport))
(defn- world-viewport ^Viewport [ctx] (-> ctx this :world-view :viewport))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn))

  (gui-mouse-position    [ctx] (g/gui-mouse-position   (this ctx)))
  (world-mouse-position  [ctx] (g/world-mouse-position (this ctx)))

  (gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport ctx)))
  (gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport ctx)))

  (world-camera          [ctx] (.getCamera      (world-viewport ctx)))
  (world-viewport-width  [ctx] (.getWorldWidth  (world-viewport ctx)))
  (world-viewport-height [ctx] (.getWorldHeight (world-viewport ctx)))

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a)))

  (set-cursor! [ctx cursor-key]
    (.setCursor Gdx/graphics (utils/safe-get (:cursors (this ctx)) cursor-key)))

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
