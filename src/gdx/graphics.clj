(ns gdx.graphics
  (:require [component.core :refer [defc]]
            [component.schema :as schema]
            [component.tx :as tx]
            [gdx.assets :as assets]
            [gdx.graphics.batch :as batch]
            [gdx.graphics.shape-drawer :as sd]
            [gdx.graphics.text :as text]
            [gdx.graphics.viewport :as vp]
            [gdx.tiled :as t]
            [gdx.utils :refer [gdx-field]]
            [utils.core :refer [bind-root safe-get]])
  (:import (com.badlogic.gdx Gdx)
           (com.badlogic.gdx.graphics Color Colors OrthographicCamera Texture Pixmap)
           (com.badlogic.gdx.graphics.g2d SpriteBatch TextureRegion)
           (com.badlogic.gdx.utils Disposable)
           (com.badlogic.gdx.utils.viewport FitViewport))
  (:load "graphics/color"
         "graphics_sd"))

(declare batch)

(def ^:private ^:dynamic *unit-scale* 1)

(load "graphics_views"
      "graphics_image")

(defn delta-time        [] (.getDeltaTime       Gdx/graphics))
(defn frames-per-second [] (.getFramesPerSecond Gdx/graphics))

(defn- ->default-font [true-type-font]
  (or (and true-type-font (text/truetype-font true-type-font))
      (text/default-font)))

(declare ^:private default-font)

(defn draw-text
  "font, h-align, up? and scale are optional.
  h-align one of: :center, :left, :right. Default :center.
  up? renders the font over y, otherwise under.
  scale will multiply the drawn text size with the scale."
  [{:keys [x y text font h-align up? scale] :as opts}]
  (text/draw (or font default-font) *unit-scale* batch opts))

(defn- mapvals [f m]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (.internal Gdx/files file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (.dispose pixmap)
    cursor))

(defn- ->cursors [cursors]
  (mapvals (fn [[file hotspot]]
             (->cursor (str "cursors/" file ".png") hotspot))
           cursors))

(declare ^:private cursors)

(defn set-cursor! [cursor-key]
  (.setCursor Gdx/graphics (safe-get cursors cursor-key)))

(defc :tx/cursor
  (tx/do! [[_ cursor-key]]
    (set-cursor! cursor-key)
    nil))

(declare ^:private cached-map-renderer)

(defn draw-tiled-map
  "Renders tiled-map using world-view at world-camera position and with world-unit-scale.

  Color-setter is a `(fn [color x y])` which is called for every tile-corner to set the color.

  Can be used for lights & shadows.

  Renders only visible layers."
  [tiled-map color-setter]
  (t/render-tm! (cached-map-renderer tiled-map)
                color-setter
                (world-camera)
                tiled-map))

(defn- ->tiled-map-renderer [tiled-map]
  (t/->orthogonal-tiled-map-renderer tiled-map (world-unit-scale) batch))

(defn load! [{:keys [views default-font cursors]}]
  (let [batch (SpriteBatch.)
        {:keys [shape-drawer shape-drawer-texture]} (sd/create batch)]
    (bind-root #'batch batch)
    (bind-root #'sd shape-drawer)
    (bind-root #'sd-texture shape-drawer-texture)
    (bind-root #'cursors (->cursors cursors))
    (bind-root #'default-font (->default-font default-font))
    (bind-views! views)
    (bind-root #'cached-map-renderer (memoize ->tiled-map-renderer))))

(defn dispose! []
  (.dispose batch)
  (.dispose sd-texture)
  (.dispose default-font)
  (run! Disposable/.dispose (vals cursors)))

(defn resize! [[w h]]
  (vp/update (gui-viewport) [w h] :center-camera? true)
  (vp/update (world-viewport) [w h]))
