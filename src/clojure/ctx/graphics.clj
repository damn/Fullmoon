(in-ns 'clojure.ctx)

(defprotocol WorldView
  (pixels->world-units [_ pixels])
  (world-unit-scale [_]))

(defprotocol PShapeDrawer
  (draw-ellipse [_ position radius-x radius-y color])
  (draw-filled-ellipse [_ position radius-x radius-y color])
  (draw-circle [_ position radius color])
  (draw-filled-circle [_ position radius color])
  (draw-arc [_ center-position radius start-angle degree color])
  (draw-sector [_ center-position radius start-angle degree color])
  (draw-rectangle [_ x y w h color])
  (draw-filled-rectangle [_ x y w h color])
  (draw-line [_ start-position end-position color])
  (draw-grid [drawer leftx bottomy gridw gridh cellw cellh color])
  (with-shape-line-width [_ width draw-fn]))

(defprotocol TextDrawer
  (draw-text [_ {:keys [x y text font h-align up? scale]}]
             "font, h-align, up? and scale are optional.
             h-align one of: :center, :left, :right. Default :center.
             up? renders the font over y, otherwise under.
             scale will multiply the drawn text size with the scale."))

(defprotocol ImageDraw
  (draw-image [_ image position])
  (draw-centered-image [_ image position])
  (draw-rotated-centered-image [_ image rotation position]))

(defrecord Graphics [batch
                     shape-drawer
                     gui-view
                     world-view
                     default-font
                     unit-scale
                     cursors])

(defn ->texture-region
  ([^Texture tex]
   (TextureRegion. tex))

  ([^TextureRegion texture-region [x y w h]]
   (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn- unit-dimensions [image unit-scale]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(defn- assoc-dimensions
  "scale can be a number for multiplying the texture-region-dimensions or [w h]."
  [{:keys [texture-region] :as image} g scale]
  {:pre [(or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (world-unit-scale g)))))

; TODO [x y] is center or left-bottom ?
; why rotation origin calculations ?!
(defn- draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
  (if color (.setColor batch color)) ; TODO move out, simplify ....
  (.draw batch
         texture-region
         x
         y
         (/ (float w) 2) ; rotation origin
         (/ (float h) 2)
         w ; width height
         h
         1 ; scaling factor
         1
         rotation)
  (if color (.setColor batch Color/WHITE)))

(extend-type Graphics
  ImageDraw
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (draw-texture-region batch
                         texture-region
                         position
                         (unit-dimensions image unit-scale)
                         0 ; rotation
                         color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture-region color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions image unit-scale)]
      (draw-texture-region batch
                           texture-region
                           [(- (float x) (/ (float w) 2))
                            (- (float y) (/ (float h) 2))]
                           [w h]
                           rotation
                           color)))

  (draw-centered-image [this image position]
    (draw-rotated-centered-image this image 0 position)))

(defn- ->image* [g texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions g 1)
      map->Image))

(defn ->image [{g :context/graphics :as ctx} file]
  (->image* g (->texture-region (texture ctx file)))) ; TODO why doesnt texture work?

(defn sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
  (->image* g (->texture-region texture-region bounds)))

(defn sprite-sheet [ctx file tilew tileh]
  {:image (->image ctx file)
   :tilew tilew
   :tileh tileh})

(defn sprite
  "x,y index starting top-left"
  [ctx {:keys [image tilew tileh]} [x y]]
  (sub-image ctx image [(* x tilew) (* y tileh) tilew tileh]))

(defn edn->image [{:keys [file sub-image-bounds]} ctx]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (sprite ctx
              (sprite-sheet ctx file tilew tileh)
              [(int (/ sprite-x tilew))
               (int (/ sprite-y tileh))]))
    (->image ctx file)))

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
  (let [generator (FreeTypeFontGenerator. (internal-file file))
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

(extend-type Graphics
  TextDrawer
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

;; graphics/shape

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

(extend-type Graphics
  PShapeDrawer
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

;; graphics views gui/world

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

(extend-type Graphics
  WorldView
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
  (let [mouse-x (clamp (gdx/input-x)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (gdx/input-y)
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

(defn gui-mouse-position    [ctx] (gui-mouse-position*             (gr ctx)))
(defn world-mouse-position  [ctx] (world-mouse-position*           (gr ctx)))
(defn gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport   (gr ctx))))
(defn gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport   (gr ctx))))
(defn world-camera          [ctx] (.getCamera      (world-viewport (gr ctx))))
(defn world-viewport-width  [ctx] (.getWorldWidth  (world-viewport (gr ctx))))
(defn world-viewport-height [ctx] (.getWorldHeight (world-viewport (gr ctx))))

(defn- ->cursor [file hotspot]
  (let [pixmap (Pixmap. (internal-file file))
        cursor (gdx/->cursor pixmap hotspot)]
    (.dispose pixmap)
    cursor))

(defn- mapvals [f m]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn- ->cursors [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(defn set-cursor! [{g :context/graphics} cursor-key]
  (gdx/set-cursor! (safe-get (:cursors g) cursor-key)))

;; ctx/graphics

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

(defn render-world-view
  "render-fn is a function of param 'g', graphics context."
  [ctx render-fn]
  (render-view ctx :world-view render-fn))

(defn- on-resize [{g :context/graphics} w h]
  (.update (gui-viewport g) w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update (world-viewport g) w h false))


(defprotocol DrawItemOnCursor
  (draw-item-on-cursor [g ctx]))
