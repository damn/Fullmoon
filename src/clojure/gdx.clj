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
         "gdx/properties"
         "gdx/app/assets"
         "gdx/app/input"
         "gdx/app/graphics"
         "gdx/app/screens"
         "gdx/app/ui"
         "gdx/app/start"
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
         "gdx/doc"))
