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
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent] :as component]
            api.context
            [api.disposable :refer [dispose]]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.maps.tiled :as tiled]
            context.libgdx.ttf-generator ; TODO
            [app.libgdx.utils.reflect :refer [bind-roots]])
  (:import com.badlogic.gdx.Gdx
           (com.badlogic.gdx.graphics Color Texture Pixmap Pixmap$Format OrthographicCamera)
           (com.badlogic.gdx.graphics.g2d Batch SpriteBatch BitmapFont TextureRegion)
           [com.badlogic.gdx.maps MapRenderer MapLayer]
           com.badlogic.gdx.utils.Align
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.math MathUtils Vector2)
           space.earlygrey.shapedrawer.ShapeDrawer
           [gdl OrthogonalTiledMapRendererWithColorSetter ColorSetter]))

(defn- this [ctx] (:context.libgdx/graphics ctx))

(extend-type api.context.Context
  api.context/Graphics
  (delta-time        [_] (.getDeltaTime       Gdx/graphics))
  (frames-per-second [_] (.getFramesPerSecond Gdx/graphics))

  (gui-mouse-position    [ctx] (g/gui-mouse-position    (this ctx)))
  (world-mouse-position  [ctx] (g/world-mouse-position  (this ctx)))

  (gui-viewport-width    [ctx] (:gui-viewport-width    (this ctx)))
  (gui-viewport-height   [ctx] (:gui-viewport-height   (this ctx)) )
  (world-camera          [ctx] (:world-camera          (this ctx)))
  (world-viewport-width  [ctx] (:world-viewport-width  (this ctx)))
  (world-viewport-height [ctx] (:world-viewport-height (this ctx)) )

  ; https://libgdx.com/wiki/input/cursor-visibility-and-catching
  (->cursor [_ file hotspot-x hotspot-y]
    (.newCursor Gdx/graphics
                (Pixmap. (.internal Gdx/files file))
                hotspot-x
                hotspot-y))

  (set-cursor! [_ cursor]
    (.setCursor Gdx/graphics cursor))

  (->color [_ r g b a]
    (Color. (float r) (float g) (float b) (float a))))

(bind-roots "com.badlogic.gdx.graphics.Color"
            'com.badlogic.gdx.graphics.Color
            "api.graphics.color")

(defrecord Graphics [batch
                     shape-drawer
                     default-font
                     unit-scale
                     gui-camera
                     gui-viewport
                     gui-viewport-width
                     gui-viewport-height
                     world-unit-scale
                     world-camera
                     world-viewport
                     world-viewport-width
                     world-viewport-height])

;; Shape Drawer

(defn- degree->radians [degree]
  (* (float degree) MathUtils/degreesToRadians))

(defn- ->Color
  ([r g b]
   (->Color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defn- ->color ^Color [color]
  (if (= Color (class color))
    color
    (apply ->Color color)))

(defn- set-color [^ShapeDrawer shape-drawer color]
  (.setColor shape-drawer (->color color)))

(extend-type Graphics
  api.graphics/ShapeDrawer
  (draw-ellipse [{:keys [^ShapeDrawer shape-drawer]} [x y] radius-x radius-y color]
    (set-color shape-drawer color)
    (.ellipse shape-drawer (float x) (float y) (float radius-x) (float radius-y)) )

  (draw-filled-ellipse [{:keys [^ShapeDrawer shape-drawer]} [x y] radius-x radius-y color]
    (set-color shape-drawer color)
    (.filledEllipse shape-drawer (float x) (float y) (float radius-x) (float radius-y)))

  (draw-circle [{:keys [^ShapeDrawer shape-drawer]} [x y] radius color]
    (set-color shape-drawer color)
    (.circle shape-drawer (float x) (float y) (float radius)))

  (draw-filled-circle [{:keys [^ShapeDrawer shape-drawer]} [x y] radius color]
    (set-color shape-drawer color)
    (.filledCircle shape-drawer (float x) (float y) (float radius)))

  (draw-arc [{:keys [^ShapeDrawer shape-drawer]} [centre-x centre-y] radius start-angle degree color]
    (set-color shape-drawer color)
    (.arc shape-drawer centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))

  (draw-sector [{:keys [^ShapeDrawer shape-drawer]} [centre-x centre-y] radius start-angle degree color]
    (set-color shape-drawer color)
    (.sector shape-drawer centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))

  (draw-rectangle [{:keys [^ShapeDrawer shape-drawer]} x y w h color]
    (set-color shape-drawer color)
    (.rectangle shape-drawer x y w h) )

  (draw-filled-rectangle [{:keys [^ShapeDrawer shape-drawer]} x y w h color]
    (set-color shape-drawer color)
    (.filledRectangle shape-drawer (float x) (float y) (float w) (float h)) )

  (draw-line [{:keys [^ShapeDrawer shape-drawer]} [sx sy] [ex ey] color]
    (set-color shape-drawer color)
    (.line shape-drawer (float sx) (float sy) (float ex) (float ey)))

  (draw-grid [this leftx bottomy gridw gridh cellw cellh color]
    (let [w (* (float gridw) (float cellw))
          h (* (float gridh) (float cellh))
          topy (+ (float bottomy) (float h))
          rightx (+ (float leftx) (float w))]
      (doseq [idx (range (inc (float gridw)))
              :let [linex (+ (float leftx) (* (float idx) (float cellw)))]]
        (g/draw-line this [linex topy] [linex bottomy] color))
      (doseq [idx (range (inc (float gridh)))
              :let [liney (+ (float bottomy) (* (float idx) (float cellh)))]]
        (g/draw-line this [leftx liney] [rightx liney] color))))

  (with-shape-line-width [{:keys [^ShapeDrawer shape-drawer]} width draw-fn]
    (let [old-line-width (.getDefaultLineWidth shape-drawer)]
      (.setDefaultLineWidth shape-drawer (float (* (float width) old-line-width)))
      (draw-fn)
      (.setDefaultLineWidth shape-drawer (float old-line-width)))))

(defn- ->shape-drawer [batch]
  (let [texture (let [pixmap (doto (Pixmap. 1 1 Pixmap$Format/RGBA8888)
                               (.setColor ^Color color/white)
                               (.drawPixel 0 0))
                      texture (Texture. pixmap)]
                  (.dispose pixmap)
                  texture)]
    {:shape-drawer (ShapeDrawer. batch (TextureRegion. texture 1 0 1 1))
     :shape-drawer-texture texture}))

;; Text Drawer

(defn- text-height [^BitmapFont font text]
  (-> text
      (str/split #"\n")
      count
      (* (.getLineHeight font))))

(extend-type Graphics
  api.graphics/TextDrawer
  (draw-text [{:keys [default-font unit-scale batch]}
              {:keys [x y text font h-align up? scale]}]
    (let [^BitmapFont font (or font default-font)
          data (.getData font)
          old-scale (float (.scaleX data))]
      (.setScale data (* old-scale (float unit-scale) (float (or scale 1))))
      (.draw font
             batch
             (str text)
             (float x)
             (+ (float y) (float (if up? (text-height font text) 0)))
             (float 0) ; target-width
             (case (or h-align :center)
               :center Align/center
               :left   Align/left
               :right  Align/right)
             false) ; wrap false, no need target-width
      (.setScale data old-scale))))

;; Views

(def ^:private gui-unit-scale 1)

(defn- screen-width  [] (.getWidth  Gdx/graphics))
(defn- screen-height [] (.getHeight Gdx/graphics))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [^Viewport viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn- render-view [{:keys [^Batch batch
                            shape-drawer
                            gui-camera
                            world-camera
                            world-unit-scale] :as g}
                    gui-or-world
                    draw-fn]
  (let [^OrthographicCamera camera (case gui-or-world
                                     :gui gui-camera
                                     :world world-camera)
        unit-scale (case gui-or-world
                     :gui gui-unit-scale
                     :world world-unit-scale)]
    (.setColor batch color/white) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined camera))
    (.begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

(defn update-viewports [{{:keys [gui-viewport world-viewport]} :context.libgdx/graphics} w h]
  (.update ^Viewport gui-viewport w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update ^Viewport world-viewport w h false))

(defn- viewport-fix-required? [{{:keys [^Viewport gui-viewport]} :context.libgdx/graphics}]
  (or (not= (.getScreenWidth  gui-viewport) (screen-width))
      (not= (.getScreenHeight gui-viewport) (screen-height))))

; TODO on mac osx, when resizing window, make bug report /  fix it in libgdx?
(defn fix-viewport-update
  "Sometimes the viewport update is not triggered."
  [context]
  (when (viewport-fix-required? context)
    (update-viewports context (screen-width) (screen-height))))

(extend-type Graphics
  api.graphics/GuiWorldViews
  (render-gui-view   [g render-fn] (render-view g :gui   render-fn))
  (render-world-view [g render-fn] (render-view g :world render-fn))

  (gui-mouse-position [{:keys [gui-viewport]}]
    ; TODO mapv int needed?
    (mapv int (unproject-mouse-posi gui-viewport)))

  (world-mouse-position [{:keys [world-viewport]}]
    ; TODO clamping only works for gui-viewport ? check. comment if true
    ; TODO ? "Can be negative coordinates, undefined cells."
    (unproject-mouse-posi world-viewport))

  (pixels->world-units [{:keys [world-unit-scale]} pixels]
    (* (int pixels) (float world-unit-scale))))

(defn- ->views [world-unit-scale]
  (merge {:unit-scale gui-unit-scale} ; only here because actors want to use drawing without using render-gui-view
         (let [gui-camera (OrthographicCamera.)
               gui-viewport (FitViewport. (screen-width) (screen-height) gui-camera)]
           {:gui-camera   gui-camera
            :gui-viewport gui-viewport
            :gui-viewport-width  (.getWorldWidth  gui-viewport)
            :gui-viewport-height (.getWorldHeight gui-viewport)})
         (let [world-camera (OrthographicCamera.)
               world-viewport (let [width  (* (screen-width) world-unit-scale)
                                    height (* (screen-height) world-unit-scale)
                                    y-down? false]
                                (.setToOrtho world-camera y-down? width height)
                                (FitViewport. width height world-camera))]
           {:world-unit-scale (float world-unit-scale)
            :world-camera     world-camera
            :world-viewport   world-viewport
            :world-viewport-width  (.getWorldWidth  world-viewport)
            :world-viewport-height (.getWorldHeight world-viewport)})))

;; Image Drawer

(defn- draw-texture [^Batch batch texture [x y] [w h] rotation color]
  (if color (.setColor batch color))
  (.draw batch texture ; TODO this is texture-region ?
         x
         y
         (/ (float w) 2) ; rotation origin
         (/ (float h) 2)
         w ; width height
         h
         1 ; scaling factor
         1
         rotation)
  (if color (.setColor batch color/white)))

; TODO just make in image map of unit-scales to dimensions for each view
; and get by view key ?
(defn- unit-dimensions [unit-scale image]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(extend-type Graphics
  api.graphics/ImageDrawer
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture color] :as image}
               position]
    (draw-texture batch texture position (unit-dimensions unit-scale image) 0 color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions unit-scale image)]
      (draw-texture batch
                    texture
                    [(- (float x) (/ (float w) 2))
                     (- (float y) (/ (float h) 2))]
                    [w h]
                    rotation
                    color)))

  (draw-centered-image [this image position]
    (g/draw-rotated-centered-image this image 0 position)))

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

(extend-type Graphics
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

; TODO BitmapFont does not draw world-unit-scale idk how possible, maybe setfontdata something
; (did draw world scale @ test ...)
; TODO optional world-viewport make
(defcomponent :context.libgdx/graphics {}
  (component/create [[_ {:keys [tile-size default-font]}] ctx]
    (let [batch (SpriteBatch.)]
      (map->Graphics
       (merge {:batch batch}
              (->shape-drawer batch)
              (->views (or (and tile-size
                                (/ tile-size))
                           1))
              {:default-font (or (and default-font
                                      (api.context/generate-ttf ctx default-font))
                                 (BitmapFont.))}))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font]}] _ctx]
    (dispose default-font)
    (dispose shape-drawer-texture)
    (dispose batch)))
