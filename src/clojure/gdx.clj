(ns clojure.gdx
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.math :as math]
            [clojure.pprint :refer [pprint]]
            [clojure.gdx.tiled :as t]
            [clj-commons.pretty.repl :refer [pretty-pst]]
            [data.grid2d :as g]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg])
  (:import (com.badlogic.gdx Gdx)
           (com.badlogic.gdx.assets AssetManager)
           (com.badlogic.gdx.audio Sound)
           (com.badlogic.gdx.files FileHandle)
           (com.badlogic.gdx.graphics Color Colors Texture Texture$TextureFilter OrthographicCamera Camera Pixmap Pixmap$Format)
           (com.badlogic.gdx.graphics.g2d SpriteBatch Batch BitmapFont TextureRegion)
           (com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter)
           (com.badlogic.gdx.math MathUtils Vector2 Vector3 Circle Rectangle Intersector)
           (com.badlogic.gdx.utils Align Scaling Disposable)
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.scenes.scene2d Actor Touchable Group Stage)
           (com.badlogic.gdx.scenes.scene2d.ui Widget Image Label Button Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window Tree$Node)
           (com.badlogic.gdx.scenes.scene2d.utils ClickListener ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget Tooltip VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane VisScrollPane Separator VisTree)
           (space.earlygrey.shapedrawer ShapeDrawer)
           (gdl RayCaster))
  (:load "gdx/math"
         "gdx/utils"
         "gdx/component"
         "gdx/properties"))

(declare assets
         all-sound-files
         all-texture-files)

(defn exit-app!         [] (.exit               Gdx/app))
(defn delta-time        [] (.getDeltaTime       Gdx/graphics))
(defn frames-per-second [] (.getFramesPerSecond Gdx/graphics))

(defmacro post-runnable! [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn- ->gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def ^:private ->gdx-input-button (partial ->gdx-field "Input$Buttons"))
(def ^:private ->gdx-input-key    (partial ->gdx-field "Input$Keys"))

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

(defn- set-input-processor! [processor]
  (.setInputProcessor Gdx/input processor))

(defn- kw->color [k] (->gdx-field "graphics.Color" k))

(def white Color/WHITE)
(def black Color/BLACK)

(defn ->color
  ([r g b] (->color r g b 1))
  ([r g b a] (Color. (float r) (float g) (float b) (float a))))

(defn- munge-color ^Color [color]
  (cond (= Color (class color)) color
        (keyword? color) (kw->color color)
        (vector? color) (apply ->color color)
        :else (throw (ex-info "Cannot understand color" {:color color}))))

(defn def-markup-color
  "A general purpose class containing named colors that can be changed at will. For example, the markup language defined by the BitmapFontCache class uses this class to retrieve colors and the user can define his own colors.

  [javadoc](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/graphics/Colors.html)"
  [name-str color]
  (Colors/put name-str (munge-color color)))

(def dispose! Disposable/.dispose)

(defn- internal-file ^FileHandle [path]
  (.internal Gdx/files path))

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

(defn load-assets [^AssetManager manager files class-k]
  (let [^Class klass (case class-k :sound Sound :texture Texture)]
    (doseq [file files]
      (.load manager ^String file klass))))

(defn asset-manager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))

(defn play-sound! [path] (Sound/.play (get assets path)))

(defc :tx/sound {:data :sound}
  (do! [[_ file]] (play-sound! file) nil))

(load "gdx/app/graphics"
      "gdx/app/screens"
      "gdx/app/ui"
      "gdx/screens/property_editor"
      "gdx/world"
      "gdx/entity/base"
      "gdx/entity/image"
      "gdx/entity/animation"
      "gdx/entity/movement"
      "gdx/entity/delete_after_duration"
      "gdx/entity/destroy_audiovisual"
      "gdx/entity/line"
      "gdx/entity/projectile"
      "gdx/entity/skills"
      "gdx/entity/faction"
      "gdx/entity/clickable"
      "gdx/entity/mouseover"
      "gdx/entity/temp_modifier"
      "gdx/entity/alert"
      "gdx/entity/string_effect"
      "gdx/entity/modifiers"
      "gdx/entity/inventory"
      "gdx/entity/state"
      "gdx/doc")
