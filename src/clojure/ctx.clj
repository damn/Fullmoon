(ns clojure.ctx
  "## Glossary

  | Name             | Meaning                                        |
  | ---------------  | -----------------------------------------------|
  | `component`      | vector `[k v]` |
  | `system`         | multimethod dispatching on component k |
  | `eid` , `entity` | entity atom                                    |
  | `entity*`        | entity value (defrecord `clojure.ctx.Entity`), |
  | `actor`          | A UI actor, not immutable `com.badlogic.gdx.scenes.scene2d.Actor`        |
  | `cell`/`cell*`   | Cell of the world grid or inventory  |
  | `camera`         | `com.badlogic.gdx.graphics.Camera`             |
  | `g`              | `clojure.ctx.Graphics`                        |
  | `grid`           | `data.grid2d.Grid`                             |
  | `image`          | `clojure.ctx.Image`                          |
  | `position`       | `[x y]` vector                                 |"
  {:metadoc/categories {:app "🖥️ Application"
                        :camera "🎥 Camera"
                        :color "🎨 Color"
                        :component "⚙️ Component"
                        :component-systems "🌀 Component Systems"
                        :drawing "🖌️ Drawing"
                        :entity "👾 Entity"
                        :geometry "📐 Geometry"
                        :image "📸 Image"
                        :input "🎮 Input"
                        :properties "📦 Properties"
                        :time "⏳ Time"
                        :ui "🎛️ UI"
                        :utils "🔧 Utils"
                        :world "🌎 World"}}
  (:require (clojure [set :as set]
                     [string :as str]
                     [edn :as edn]
                     [math :as math]
                     [pprint :refer [pprint]])
            [clj-commons.pretty.repl :refer [pretty-pst]]
            (malli [core :as m]
                   [error :as me]
                   [generator :as mg]))
  (:import java.util.Random
           org.lwjgl.system.Configuration
           (com.badlogic.gdx Gdx ApplicationAdapter Input$Keys)
           com.badlogic.gdx.audio.Sound
           com.badlogic.gdx.assets.AssetManager
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.files.FileHandle
           [com.badlogic.gdx.math MathUtils Vector2 Vector3 Circle Rectangle Intersector]
           (com.badlogic.gdx.graphics Color Texture Texture$TextureFilter Pixmap Pixmap$Format OrthographicCamera Camera)
           (com.badlogic.gdx.graphics.g2d TextureRegion Batch SpriteBatch BitmapFont)
           [com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter]
           (com.badlogic.gdx.utils Align Scaling Disposable ScreenUtils SharedLibraryLoader)
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.scenes.scene2d Actor Touchable Group Stage)
           (com.badlogic.gdx.scenes.scene2d.ui Label Button Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget Tooltip VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane VisScrollPane Separator)
           space.earlygrey.shapedrawer.ShapeDrawer
           gdl.RayCaster)
  (:load "ctx/utils"
         "ctx/component"
         "ctx/systems"
         "ctx/effect"
         "ctx/assets"
         "ctx/info"
         "ctx/graphics"
         "ctx/time"
         "ctx/world"
         "ctx/geometry"
         "ctx/val_max"
         "ctx/camera"
         "ctx/screens"
         "ctx/ui"
         "ctx/raycaster"
         "ctx/properties"
         "ctx/entity"
         "ctx/operation"
         "ctx/app"
         "ctx/types"
         "ctx/input"
         "ctx/txs"
         "ctx/context"
         "ctx/doc"))

(com.badlogic.gdx.graphics.Colors/put
 "ITEM_GOLD"
 (com.badlogic.gdx.graphics.Color. (float 0.84)
                                   (float 0.8)
                                   (float 0.52)
                                   (float 1)))

(defcomponent :property/pretty-name
  {:data :string
   :let value}
  (info-text [_ _ctx]
    (str "[ITEM_GOLD]"value"[]")))

(defprotocol WorldGen
  (->world [ctx world-id]))

(def ^:private ctx-msg-player :context/msg-to-player)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (ctx-msg-player ctx)]
    (draw-text g {:x (/ (gui-viewport-width ctx) 2)
                  :y (+ (/ (gui-viewport-height ctx) 2) 200)
                  :text message
                  :scale 2.5
                  :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (ctx-msg-player ctx)]
    (swap! app-state update ctx-msg-player update :counter + (.getDeltaTime gdx-graphics))
    (when (>= counter duration-seconds)
      (swap! app-state assoc ctx-msg-player nil))))

(defcomponent :widgets/player-message
  (->mk [_ _ctx]
    (->actor {:draw draw-player-message
              :act check-remove-message})))
