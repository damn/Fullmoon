; # what does it depend on ?
; * tiled/layers & tiled/layer-index => context.libgdx.tiled
; * context.libgdx.ttf-generator

; # private data usage of ':context.libgdx/graphics' ?
; * g/render-world-view ( move to ctx itself ?)
; * g/render-gui-view ( move to ctx itself ?)
; * g/render-tiled-map ( complicated beast ... !)
; * draw-rect-actor @ inventory  (pass @ draw ?)
; used @ image drawer creator ....
; * used @ ->stage-screen .... & mouse-on-stage-actor?
; ->hp-mana-bars (actor draw ...)
(ns context.libgdx.graphics
  (:require [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            [api.maps.tiled :as tiled]
            context.libgdx.graphics.shape-drawer
            context.libgdx.graphics.text-drawer
            context.libgdx.graphics.views
            [app.libgdx.utils.reflect :refer [bind-roots]])
  (:import com.badlogic.gdx.Gdx
           (com.badlogic.gdx.graphics Color Pixmap OrthographicCamera)
           com.badlogic.gdx.graphics.g2d.SpriteBatch
           [com.badlogic.gdx.maps MapRenderer MapLayer]
           [gdl OrthogonalTiledMapRendererWithColorSetter ColorSetter]))

; not simple, smell
; but its necessary to use
; * host platform colors without having to know its libgdx/java (for exapmle clojurescript, cljc files, any other plattform ...)
; * with no performance penalties for converting e.g. keywords to color instances
(bind-roots "com.badlogic.gdx.graphics.Color"
            'com.badlogic.gdx.graphics.Color
            "api.graphics.color")

; simple
(defn- this [ctx] (:context.libgdx/graphics ctx))

(extend-type api.context.Context
  api.context/Graphics
  ; simple
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  ; simple
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  ; complected files & pixmap & new cursor ....
  ; should we decide to go with libgdx and create a simple helper library ???
  ; but we are using clojure protocols in order to be able to have a different implementation ....
  (->cursor [_ file hotspot-x hotspot-y]
    (.newCursor Gdx/graphics
                (Pixmap. (.internal Gdx/files file)) ; TODO pixmap is not disposed ....
                hotspot-x
                hotspot-y))

  (set-cursor! [_ cursor]
    (.setCursor Gdx/graphics cursor))

  ; smell ...

  (gui-mouse-position    [ctx] (g/gui-mouse-position    (this ctx)))
  (world-mouse-position  [ctx] (g/world-mouse-position  (this ctx)))

  (gui-viewport-width    [ctx] (:gui-viewport-width    (this ctx)))
  (gui-viewport-height   [ctx] (:gui-viewport-height   (this ctx)))

  (world-camera          [ctx] (:world-camera          (this ctx)))
  (world-viewport-width  [ctx] (:world-viewport-width  (this ctx)))
  (world-viewport-height [ctx] (:world-viewport-height (this ctx)))

  ; weird, used in 1 place only ...
  ; smell! not simple !
  ; creates a color instance of the host plattform ...  ....
  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a))))

; OrthogonalTiledMapRenderer extends BatchTiledMapRenderer
; and when a batch is passed to the constructor
; we do not need to dispose the renderer
; TODO pass graphics
(defn- map-renderer-for [{:keys [batch world-unit-scale]}
                         tiled-map
                         color-setter]
  (OrthogonalTiledMapRendererWithColorSetter. tiled-map
                                              (float world-unit-scale)
                                              batch
                                              (reify ColorSetter
                                                (apply [_ color x y]
                                                  (color-setter color x y)))))

; TODO into graphics ?! memory loss thingy ??? FIXME
(def ^:private cached-map-renderer (memoize map-renderer-for))

(extend-type api.graphics.Graphics
  api.graphics/TiledMapRenderer
  (render-tiled-map [{:keys [world-camera] :as g} tiled-map color-setter]
    (let [^MapRenderer map-renderer (cached-map-renderer g tiled-map color-setter)]
      (.update ^OrthographicCamera world-camera)
      (.setView map-renderer world-camera)
      (->> tiled-map
           tiled/layers
           (filter #(.isVisible ^MapLayer %))
           (map (partial tiled/layer-index tiled-map))
           int-array
           (.render map-renderer)))))

; TODO optional world-viewport make
(defcomponent :context.libgdx/graphics {}
  (component/create [[_ {:keys [tile-size default-font]}] ctx]
    (let [batch (SpriteBatch.)]
      (g/map->Graphics
       (merge {:batch batch}
              (context.libgdx.graphics.shape-drawer/->shape-drawer batch)
              (context.libgdx.graphics.text-drawer/->build default-font)
              (context.libgdx.graphics.views/->views (or (and tile-size
                                                              (/ tile-size))
                                                         1))))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font]}] _ctx]
    (dispose default-font)
    (dispose shape-drawer-texture)
    (dispose batch)))
