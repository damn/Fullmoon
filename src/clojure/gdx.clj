(ns clojure.gdx
  (:require [clojure.string :as str])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.assets.AssetManager
           com.badlogic.gdx.audio.Sound
           com.badlogic.gdx.files.FileHandle
           (com.badlogic.gdx.graphics Color Colors Texture Texture$TextureFilter OrthographicCamera Camera Pixmap Pixmap$Format)
           (com.badlogic.gdx.graphics.g2d SpriteBatch Batch BitmapFont TextureRegion)
           (com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter)
           (com.badlogic.gdx.math MathUtils Vector2 Vector3 Circle Rectangle Intersector)
           (com.badlogic.gdx.utils Align SharedLibraryLoader ScreenUtils Disposable)
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           space.earlygrey.shapedrawer.ShapeDrawer
           gdl.RayCaster))

(defn exit-app!
  "Schedule an exit from the application. On android, this will cause a call to pause() and dispose() some time in the future, it will not immediately finish your application. On iOS this should be avoided in production as it breaks Apples guidelines

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#exit())"
  []
  (.exit Gdx/app))

(defmacro post-runnable!
  "Posts a Runnable on the main loop thread. In a multi-window application, the Gdx.graphics and Gdx.input values may be unpredictable at the time the Runnable is executed. If graphics or input are needed, they can be copied to a variable to be used in the Runnable. For example:

  final Graphics graphics = Gdx.graphics;

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Application.html#postRunnable(java.lang.Runnable))"
  [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn delta-time
  "the time span between the current frame and the last frame in seconds.

  `returns float`

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getDeltaTime())"
  []
  (.getDeltaTime Gdx/graphics))

(defn frames-per-second
  "the average number of frames per second

  `returns int`

  [javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Graphics.html#getFramesPerSecond())"
  []
  (.getFramesPerSecond Gdx/graphics))

(defn ->color
  "[javadoc](https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/graphics/Color.html#%3Cinit%3E(float,float,float,float))"
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defn- ->gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def ^:private ->gdx-input-button (partial ->gdx-field "Input$Buttons"))
(def ^:private ->gdx-input-key    (partial ->gdx-field "Input$Keys"))

(comment
 (and (= (->gdx-input-button :left) 0)
      (= (->gdx-input-button :forward) 4)
      (= (->gdx-input-key :shift-left) 59))
 )

; missing button-pressed?
; also not explaining just-pressed or pressed docs ...
; always link the java class (for all stuff?)
; https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Input.html#isButtonPressed(int)

(defn button-just-pressed?
  ":left, :right, :middle, :back or :forward."
  [b]
  (.isButtonJustPressed Gdx/input (->gdx-input-button b)))

(defn key-just-pressed?
  "See [[key-pressed?]]."
  [k]
  (.isKeyJustPressed Gdx/input (->gdx-input-key k)))

(defn key-pressed?
  "For options see [libgdx Input$Keys docs](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/Input.Keys.html).
  Keys are upper-cased and dashes replaced by underscores.
  For example Input$Keys/ALT_LEFT can be used with :alt-left.
  Numbers via :num-3, etc."
  [k]
  (.isKeyPressed Gdx/input (->gdx-input-key k)))

(defn set-input-processor! [processor]
  (.setInputProcessor Gdx/input processor))

(defn internal-file
  "Path relative to the asset directory on Android and to the application's root directory on the desktop. On the desktop, if the file is not found, then the classpath is checked. This enables files to be found when using JWS or applets. Internal files are always readonly."
  ^FileHandle [path]
  (.internal Gdx/files path))

(defn dispose! [obj] (Disposable/.dispose obj))

(defn ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (internal-file file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (dispose! pixmap)
    cursor))

(defn g-set-cursor! [cursor] (.setCursor Gdx/graphics cursor))

(defn- ->lwjgl3-app-config
  [{:keys [title width height full-screen? fps]}]
  {:pre [title width height (boolean? full-screen?) (or (nil? fps) (int? fps))]}
  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
  (when SharedLibraryLoader/isMac
    (.set org.lwjgl.system.Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set org.lwjgl.system.Configuration/GLFW_CHECK_THREAD0 false))
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))

(defn ->lwjgl3-app [listener config]
  (Lwjgl3Application. listener (->lwjgl3-app-config config)))

(defn ->asset-manager ^AssetManager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))

(defn load-assets! [^AssetManager manager files class-k]
  (let [^Class klass (case class-k :sound Sound :texture Texture)]
    (doseq [file files]
      (.load manager ^String file klass))))

(defn finish-loading! [^AssetManager m] (.finishLoading m))

(defn play! [sound] (Sound/.play sound))

(defn- recursively-search [folder extensions]
  (loop [[^FileHandle file & remaining] (.list (internal-file folder))
         result []]
    (cond (nil? file)
          result

          (.isDirectory file)
          (recur (concat remaining (.list file)) result)

          (extensions (.extension file))
          (recur remaining (conj result (.path file)))

          :else
          (recur remaining result))))

(defn search-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (recursively-search folder file-extensions)))

(defn equal?
  "Returns true if a is nearly equal to b."
  [a b]
  (MathUtils/isEqual a b))

(defn- degree->radians [degree]
  (* (float degree) MathUtils/degreesToRadians))

(defn clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

(defn camera-position
  "Returns camera position as [x y] vector."
  [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn camera-set-position!
  "Sets x and y and calls update on the camera."
  [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera))

(defn frustum [^Camera camera]
  (let [frustum-points (for [^Vector3 point (take 4 (.planePoints (.frustum camera)))
                             :let [x (.x point)
                                   y (.y point)]]
                         [x y])
        left-x   (apply min (map first  frustum-points))
        right-x  (apply max (map first  frustum-points))
        bottom-y (apply min (map second frustum-points))
        top-y    (apply max (map second frustum-points))]
    [left-x right-x bottom-y top-y]))

(defn visible-tiles [camera]
  (let [[left-x right-x bottom-y top-y] (frustum camera)]
    (for  [x (range (int left-x)   (int right-x))
           y (range (int bottom-y) (+ 2 (int top-y)))]
      [x y])))

(defn calculate-zoom
  "calculates the zoom value for camera to see all the 4 points."
  [^Camera camera & {:keys [left top right bottom]}]
  (let [viewport-width  (.viewportWidth  camera)
        viewport-height (.viewportHeight camera)
        [px py] (camera-position camera)
        px (float px)
        py (float py)
        leftx (float (left 0))
        rightx (float (right 0))
        x-diff (max (- px leftx) (- rightx px))
        topy (float (top 1))
        bottomy (float (bottom 1))
        y-diff (max (- topy py) (- py bottomy))
        vp-ratio-w (/ (* x-diff 2) viewport-width)
        vp-ratio-h (/ (* y-diff 2) viewport-height)
        new-zoom (max vp-ratio-w vp-ratio-h)]
    new-zoom))

(defn zoom [^OrthographicCamera camera]
  (.zoom camera))

(defn set-zoom!
  "Sets the zoom value and updates."
  [^OrthographicCamera camera amount]
  (set! (.zoom camera) amount)
  (.update camera))

(defn reset-zoom!
  "Sets the zoom value to 1."
  [camera]
  (set-zoom! camera 1))

(defn- ->camera ^OrthographicCamera [] (OrthographicCamera.))

(defn clear-screen! [] (ScreenUtils/clear Color/BLACK))

; TODO not important badlogic, using clojure vectors
; could extend some protocol by clojure vectors and just require the protocol
; also call vector2 v2/add ? v2/scale ?

(defn- ^Vector2 ->v [[x y]]
  (Vector2. x y))

(defn- ->p [^Vector2 v]
  [(.x ^Vector2 v)
   (.y ^Vector2 v)])

(defn v-scale     [v n]    (->p (.scl ^Vector2 (->v v) (float n)))) ; TODO just (mapv (partial * 2) v)
(defn v-normalise [v]      (->p (.nor ^Vector2 (->v v))))
(defn v-add       [v1 v2]  (->p (.add ^Vector2 (->v v1) ^Vector2 (->v v2))))
(defn v-length    [v]      (.len ^Vector2 (->v v)))
(defn v-distance  [v1 v2]  (.dst ^Vector2 (->v v1) ^Vector2 (->v v2)))

(defn v-normalised? [v] (equal? 1 (v-length v)))

(defn v-get-normal-vectors [[x y]]
  [[(- (float y))         x]
   [          y (- (float x))]])

(defn v-direction [[sx sy] [tx ty]]
  (v-normalise [(- (float tx) (float sx))
                (- (float ty) (float sy))]))

(defn v-get-angle-from-vector
  "converts theta of Vector2 to angle from top (top is 0 degree, moving left is 90 degree etc.), ->counterclockwise"
  [v]
  (.angleDeg (->v v) (Vector2. 0 1)))

(comment

 (pprint
  (for [v [[0 1]
           [1 1]
           [1 0]
           [1 -1]
           [0 -1]
           [-1 -1]
           [-1 0]
           [-1 1]]]
    [v
     (.angleDeg (->v v) (Vector2. 0 1))
     (get-angle-from-vector (->v v))]))

 )

(defn- add-vs [vs]
  (v-normalise (reduce v-add [0 0] vs)))

(defn WASD-movement-vector []
  (let [r (when (key-pressed? :d) [1  0])
        l (when (key-pressed? :a) [-1 0])
        u (when (key-pressed? :w) [0  1])
        d (when (key-pressed? :s) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v-length v))
          v)))))

(defn- ->circle [[x y] radius]
  (Circle. x y radius))

(defn- ->rectangle [[x y] width height]
  (Rectangle. x y width height))

(defn- rect-contains? [^Rectangle rectangle [x y]]
  (.contains rectangle x y))

(defmulti ^:private overlaps? (fn [a b] [(class a) (class b)]))

(defmethod overlaps? [Circle Circle]
  [^Circle a ^Circle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Rectangle]
  [^Rectangle a ^Rectangle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Circle]
  [^Rectangle rect ^Circle circle]
  (Intersector/overlaps circle rect))

(defmethod overlaps? [Circle Rectangle]
  [^Circle circle ^Rectangle rect]
  (Intersector/overlaps circle rect))

(defn- rectangle? [{[x y] :left-bottom :keys [width height]}]
  (and x y width height))

(defn- circle? [{[x y] :position :keys [radius]}]
  (and x y radius))

(defn- m->shape [m]
  (cond
   (rectangle? m) (let [{:keys [left-bottom width height]} m]
                    (->rectangle left-bottom width height))

   (circle? m) (let [{:keys [position radius]} m]
                 (->circle position radius))

   :else (throw (Error. (str m)))))

(defn shape-collides? [a b]
  (overlaps? (m->shape a) (m->shape b)))

(defn point-in-rect? [point rectangle]
  (rect-contains? (m->shape rectangle) point))

(defn circle->outer-rectangle [{[x y] :position :keys [radius] :as circle}]
  {:pre [(circle? circle)]}
  (let [radius (float radius)
        size (* radius 2)]
    {:left-bottom [(- (float x) radius)
                   (- (float y) radius)]
     :width  size
     :height size}))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn unproject-mouse-posi
  "Returns vector of [x y]."
  [^Viewport viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn ->gui-viewport [world-width world-height]
  (FitViewport. world-width world-height (->camera)))

(defn ->world-viewport [world-width world-height unit-scale]
  (let [world-width  (* world-width  unit-scale)
        world-height (* world-height unit-scale)
        camera (->camera)
        y-down? false]
    (.setToOrtho camera y-down? world-width world-height)
    (FitViewport. world-width world-height camera)))

(defn ->texture-region
  ([^Texture tex]
   (TextureRegion. tex))

  ([^TextureRegion texture-region [x y w h]]
   (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

(defn texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(defn ->sprite-batch [] (SpriteBatch.))

; TODO [x y] is center or left-bottom ?
; why rotation origin calculations ?!
(defn draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
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

(defn draw-with! [^Batch batch ^Viewport viewport draw-fn]
  (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
  (.setProjectionMatrix batch (.combined (.getCamera viewport)))
  (.begin batch)
  (draw-fn)
  (.end batch))

(defn- ->shape-drawer-texture ^Texture []
  (let [pixmap (doto (Pixmap. 1 1 Pixmap$Format/RGBA8888)
                 (.setColor Color/WHITE)
                 (.drawPixel 0 0))
        tex (Texture. pixmap)]
    (dispose! pixmap)
    tex))

(defn ->shape-drawer [batch]
  (let [tex (->shape-drawer-texture)]
    {:shape-drawer (ShapeDrawer. batch (TextureRegion. tex 1 0 1 1))
     :shape-drawer-texture tex}))

(defn- kw->color [k] (->gdx-field "graphics.Color" k))

(comment
 (and (= Color/WHITE      (kw->color :white))
      (= Color/LIGHT_GRAY (kw->color :light-gray)))
 )

(def white Color/WHITE)
(def black Color/BLACK)

(defn- munge-color ^Color [color]
  (cond
   (= Color (class color)) color
   (keyword? color) (kw->color color)
   (vector? color) (apply ->color color)
   :else (throw (ex-info "Cannot understand color" {:color color}))))

(defn def-markup-color
  "A general purpose class containing named colors that can be changed at will. For example, the markup language defined by the BitmapFontCache class uses this class to retrieve colors and the user can define his own colors.

  [javadoc](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/graphics/Colors.html)"
  [name-str color]
  (Colors/put name-str (munge-color color)))

(defn set-color! [^ShapeDrawer shape-drawer color]
  (.setColor shape-drawer (munge-color color)))

(defn sd-ellipse [^ShapeDrawer sd [x y] radius-x radius-y]
  (.ellipse sd (float x) (float y) (float radius-x) (float radius-y)))

(defn sd-filled-ellipse [^ShapeDrawer sd [x y] radius-x radius-y]
  (.filledEllipse sd (float x) (float y) (float radius-x) (float radius-y)))

(defn sd-circle [^ShapeDrawer sd [x y] radius]
  (.circle sd (float x) (float y) (float radius)))

(defn sd-filled-circle [^ShapeDrawer sd [x y] radius]
  (.filledCircle sd (float x) (float y) (float radius)))

(defn sd-arc [^ShapeDrawer sd [centre-x centre-y] radius start-angle degree]
  (.arc sd centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))

(defn sd-sector [^ShapeDrawer sd [centre-x centre-y] radius start-angle degree]
  (.sector sd centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))

(defn sd-rectangle [^ShapeDrawer sd x y w h]
  (.rectangle sd x y w h))

(defn sd-filled-rectangle [^ShapeDrawer sd x y w h]
  (.filledRectangle sd (float x) (float y) (float w) (float h)) )

(defn sd-line [^ShapeDrawer sd [sx sy] [ex ey]]
  (.line sd (float sx) (float sy) (float ex) (float ey)))

(defn sd-grid [sd leftx bottomy gridw gridh cellw cellh]
  (let [w (* (float gridw) (float cellw))
        h (* (float gridh) (float cellh))
        topy (+ (float bottomy) (float h))
        rightx (+ (float leftx) (float w))]
    (doseq [idx (range (inc (float gridw)))
            :let [linex (+ (float leftx) (* (float idx) (float cellw)))]]
      (sd-line sd [linex topy] [linex bottomy]))
    (doseq [idx (range (inc (float gridh)))
            :let [liney (+ (float bottomy) (* (float idx) (float cellh)))]]
      (sd-line sd [leftx liney] [rightx liney]))))

(defn sd-with-line-width [^ShapeDrawer sd width draw-fn]
  (let [old-line-width (.getDefaultLineWidth sd)]
    (.setDefaultLineWidth sd (float (* (float width) old-line-width)))
    (draw-fn)
    (.setDefaultLineWidth sd (float old-line-width))))

(defn- ->ttf-params [size quality-scaling]
  (let [params (FreeTypeFontGenerator$FreeTypeFontParameter.)]
    (set! (.size params) (* size quality-scaling))
    ; .color and this:
    ;(set! (.borderWidth parameter) 1)
    ;(set! (.borderColor parameter) red)
    (set! (.minFilter params) Texture$TextureFilter/Linear) ; because scaling to world-units
    (set! (.magFilter params) Texture$TextureFilter/Linear)
    params))

(defn generate-ttf [{:keys [file size quality-scaling]}]
  (let [generator (FreeTypeFontGenerator. (internal-file file))
        font (.generateFont generator (->ttf-params size quality-scaling))]
    (dispose! generator)
    (.setScale (.getData font) (float (/ quality-scaling)))
    (set! (.markupEnabled (.getData font)) true)
    (.setUseIntegerPositions font false) ; otherwise scaling to world-units (/ 1 48)px not visible
    font))

(defn gdx-default-font [] (BitmapFont.))

(defn- text-height [^BitmapFont font text]
  (-> text
      (str/split #"\n")
      count
      (* (.getLineHeight font))))

(defn font-draw [^BitmapFont font
                 unit-scale
                 batch
                 {:keys [x y text h-align up? scale]}]
  (let [data (.getData font)
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
    (.setScale data old-scale)))

(defprotocol PFastRayCaster
  (fast-ray-blocked? [_ start target]))

; boolean array used because 10x faster than access to clojure grid data structure

; this was a serious performance bottleneck -> alength is counting the whole array?
;(def ^:private width alength)
;(def ^:private height (comp alength first))

; does not show warning on reflection, but shows cast-double a lot.
(defrecord ArrRayCaster [arr width height]
  PFastRayCaster
  (fast-ray-blocked? [_ [start-x start-y] [target-x target-y]]
    (RayCaster/rayBlocked (double start-x)
                          (double start-y)
                          (double target-x)
                          (double target-y)
                          width ;(width boolean-2d-array)
                          height ;(height boolean-2d-array)
                          arr)))

#_(defn ray-steplist [boolean-2d-array [start-x start-y] [target-x target-y]]
  (seq
   (RayCaster/castSteplist start-x
                           start-y
                           target-x
                           target-y
                           (width boolean-2d-array)
                           (height boolean-2d-array)
                           boolean-2d-array)))

#_(defn ray-maxsteps [boolean-2d-array  [start-x start-y] [vector-x vector-y] max-steps]
  (let [erg (RayCaster/castMaxSteps start-x
                                    start-y
                                    vector-x
                                    vector-y
                                    (width boolean-2d-array)
                                    (height boolean-2d-array)
                                    boolean-2d-array
                                    max-steps
                                    max-steps)]
    (if (= -1 erg)
      :not-blocked
      erg)))

; STEPLIST TEST

#_(def current-steplist (atom nil))

#_(defn steplist-contains? [tilex tiley] ; use vector equality
  (some
    (fn [[x y]]
      (and (= x tilex) (= y tiley)))
    @current-steplist))

#_(defn render-line-middle-to-mouse [color]
  (let [[x y] (input/get-mouse-pos)]
    (g/draw-line (/ (g/viewport-width) 2)
                 (/ (g/viewport-height) 2)
                 x y color)))

#_(defn update-test-raycast-steplist []
    (reset! current-steplist
            (map
             (fn [step]
               [(.x step) (.y step)])
             (raycaster/ray-steplist (get-cell-blocked-boolean-array)
                                     (:position @player-entity)
                                     (g/map-coords)))))

;; MAXSTEPS TEST

#_(def current-steps (atom nil))

#_(defn update-test-raycast-maxsteps []
    (let [maxsteps 10]
      (reset! current-steps
              (raycaster/ray-maxsteps (get-cell-blocked-boolean-array)
                                      (v-direction (g/map-coords) start)
                                      maxsteps))))

#_(defn draw-test-raycast []
  (let [start (:position @player-entity)
        target (g/map-coords)
        color (if (fast-ray-blocked? start target) g/red g/green)]
    (render-line-middle-to-mouse color)))

; PATH BLOCKED TEST

#_(defn draw-test-path-blocked [] ; TODO draw in map no need for screenpos-of-tilepos
  (let [[start-x start-y] (:position @player-entity)
        [target-x target-y] (g/map-coords)
        [start1 target1 start2 target2] (create-double-ray-endpositions start-x start-y target-x target-y 0.4)
        [start1screenx,start1screeny]   (screenpos-of-tilepos start1)
        [target1screenx,target1screeny] (screenpos-of-tilepos target1)
        [start2screenx,start2screeny]   (screenpos-of-tilepos start2)
        [target2screenx,target2screeny] (screenpos-of-tilepos target2)
        color (if (is-path-blocked? start1 target1 start2 target2)
                g/red
                g/green)]
    (g/draw-line start1screenx start1screeny target1screenx target1screeny color)
    (g/draw-line start2screenx start2screeny target2screenx target2screeny color)))
