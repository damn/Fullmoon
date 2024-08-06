(ns context.graphics
  (:require [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            graphics.shape-drawer
            graphics.image
            graphics.text
            graphics.cursors
            graphics.views)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Color
           [com.badlogic.gdx.graphics.g2d SpriteBatch]))

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
    (Color. (float r) (float g) (float b) (float a))))
