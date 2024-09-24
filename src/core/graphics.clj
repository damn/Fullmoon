(ns core.graphics
  (:require [clojure.string :as str]
            [core.utils.core :refer [mapvals]]
            [core.ctx :refer :all])
  (:import (com.badlogic.gdx.graphics Color Texture Texture$TextureFilter Pixmap Pixmap$Format OrthographicCamera)
           (com.badlogic.gdx.graphics.g2d Batch SpriteBatch TextureRegion BitmapFont)
           [com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter]
           [com.badlogic.gdx.math MathUtils Vector2]
           com.badlogic.gdx.utils.Align
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           space.earlygrey.shapedrawer.ShapeDrawer))

(defn- ->params [size quality-scaling]
  (let [params (FreeTypeFontGenerator$FreeTypeFontParameter.)]
    (set! (.size params) (* size quality-scaling))
    ; .color and this:
    ;(set! (.borderWidth parameter) 1)
    ;(set! (.borderColor parameter) red)
    (set! (.minFilter params) Texture$TextureFilter/Linear) ; because scaling to world-units
    (set! (.magFilter params) Texture$TextureFilter/Linear)
    params))

(defn- generate-ttf [{:keys [file size quality-scaling]}]
  (let [generator (FreeTypeFontGenerator. (.internal gdx-files file))
        font (.generateFont generator (->params size quality-scaling))]
    (.dispose generator)
    (.setScale (.getData font) (float (/ quality-scaling)))
    (set! (.markupEnabled (.getData font)) true)
    (.setUseIntegerPositions font false) ; otherwise scaling to world-units (/ 1 48)px not visible
    font))

(defn- ->default-font [default-font]
  {:default-font (or (and default-font (generate-ttf default-font))
                     (BitmapFont.))})

(defn- text-height [^BitmapFont font text]
  (-> text
      (str/split #"\n")
      count
      (* (.getLineHeight font))))

(extend-type core.ctx.Graphics
  core.ctx/TextDrawer
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

(defn- ->shape-drawer [batch]
  (let [tex (let [pixmap (doto (Pixmap. 1 1 Pixmap$Format/RGBA8888)
                           (.setColor ^Color Color/WHITE)
                           (.drawPixel 0 0))
                  tex (Texture. pixmap)]
              (.dispose pixmap)
              tex)]
    {:shape-drawer (ShapeDrawer. batch (TextureRegion. tex 1 0 1 1))
     :shape-drawer-texture tex}))

(defn- degree->radians [degree]
  (* (float degree) MathUtils/degreesToRadians))

(defn- munge-color ^Color [color]
  (if (= Color (class color))
    color
    (apply ->color color)))

(defn- set-color [^ShapeDrawer shape-drawer color]
  (.setColor shape-drawer (munge-color color)))

(extend-type core.ctx.Graphics
  core.ctx/PShapeDrawer
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
        (draw-line this [linex topy] [linex bottomy] color))
      (doseq [idx (range (inc (float gridh)))
              :let [liney (+ (float bottomy) (* (float idx) (float cellh)))]]
        (draw-line this [leftx liney] [rightx liney] color))))

  (with-shape-line-width [{:keys [^ShapeDrawer shape-drawer]} width draw-fn]
    (let [old-line-width (.getDefaultLineWidth shape-drawer)]
      (.setDefaultLineWidth shape-drawer (float (* (float width) old-line-width)))
      (draw-fn)
      (.setDefaultLineWidth shape-drawer (float old-line-width)))))

(defn- ->gui-view [{:keys [world-width world-height]}]
  {:unit-scale 1
   :viewport (FitViewport. world-width
                           world-height
                           (OrthographicCamera.))})

(defn- ->world-view [{:keys [world-width world-height tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (let [world-width  (* world-width  unit-scale)
                     world-height (* world-height unit-scale)
                     camera (OrthographicCamera.)
                     y-down? false]
                 (.setToOrtho camera y-down? world-width world-height)
                 (FitViewport. world-width world-height camera))}))

(defn- ->views [{:keys [gui-view world-view]}]
  {:gui-view (->gui-view gui-view)
   :world-view (->world-view world-view)})

(extend-type core.ctx.Graphics
  core.ctx/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (world-unit-scale g))))

(defn- gui-viewport   ^Viewport [g] (-> g :gui-view   :viewport))
(defn- world-viewport ^Viewport [g] (-> g :world-view :viewport))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [^Viewport viewport]
  (let [mouse-x (clamp (.getX gdx-input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY gdx-input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn- gui-mouse-position* [g]
  ; TODO mapv int needed?
  (mapv int (unproject-mouse-posi (gui-viewport g))))

(defn- world-mouse-position* [g]
  ; TODO clamping only works for gui-viewport ? check. comment if true
  ; TODO ? "Can be negative coordinates, undefined cells."
  (unproject-mouse-posi (world-viewport g)))

(defn- gr [ctx] (:context/graphics ctx))

(extend-type core.ctx.Context
  Views
  (gui-mouse-position    [ctx] (gui-mouse-position*             (gr ctx)))
  (world-mouse-position  [ctx] (world-mouse-position*           (gr ctx)))
  (gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport   (gr ctx))))
  (gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport   (gr ctx))))
  (world-camera          [ctx] (.getCamera      (world-viewport (gr ctx))))
  (world-viewport-width  [ctx] (.getWorldWidth  (world-viewport (gr ctx))))
  (world-viewport-height [ctx] (.getWorldHeight (world-viewport (gr ctx)))))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (.internal gdx-files file))
        cursor (.newCursor gdx-graphics pixmap hotspot-x hotspot-y)]
    (.dispose pixmap)
    cursor))

(defn- ->cursors [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(defn set-cursor! [{g :context/graphics} cursor-key]
  (.setCursor gdx-graphics (safe-get (:cursors g) cursor-key)))

(defcomponent :tx/cursor
  (do! [[_ cursor-key] ctx]
    (set-cursor! ctx cursor-key)
    ctx))

(def-attributes
  :views [:map [:gui-view :world-view]]
  :gui-view [:map [:world-width :world-height]]
  :world-view [:map [:tile-size :world-width :world-height]]
  :world-width :pos-int
  :world-height :pos-int
  :tile-size :pos-int
  :default-font [:map [:file :quality-scaling :size]]
  :file :string
  :quality-scaling :pos-int
  :size :pos-int
  :cursors :some)

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (->mk [_ _ctx]
    (map->Graphics
     (let [batch (SpriteBatch.)]
       (merge {:batch batch}
              (->shape-drawer batch)
              (->default-font default-font)
              (->views views)
              (->cursors cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(defn- render-view [{{:keys [^Batch batch] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (with-shape-line-width g
      unit-scale
      #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

(defn render-gui-view
  "render-fn is a function of param 'g', graphics context."
  [ctx render-fn]
  (render-view ctx :gui-view render-fn))

(extend-type core.ctx.Context
  RenderWorldView
  (render-world-view [ctx render-fn]
    (render-view ctx :world-view render-fn)) )

(defn on-resize [{g :context/graphics} w h]
  (.update (gui-viewport g) w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update (world-viewport g) w h false))
