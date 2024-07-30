(ns gdl.context
  (:require [core.component :as component]))

(defrecord Context [])

(component/defn create  [_ ctx])
(component/defn destroy [_ ctx])
(component/defn render  [_ ctx])

(defprotocol Application
  (exit-app [_]))

(defprotocol ApplicationScreens
  (current-screen [_])
  (change-screen [_ new-screen]
                 "Calls screen/hide on the current-screen (if there is one).
                 Throws AssertionError when the context does not have a new-screen.
                 Calls screen/show on the new screen and
                 returns the context with current-screen set to new-screen."))

(defprotocol Graphics
  (delta-time [_] "the time span between the current frame and the last frame in seconds.")
  (frames-per-second [_] "the average number of frames per second")

  (gui-mouse-position   [_])
  (gui-viewport-width   [_])
  (gui-viewport-height  [_])

  (world-mouse-position  [_])
  (world-camera          [_])
  (world-viewport-width  [_])
  (world-viewport-height [_])

  (->cursor [_ file hotspot-x hotspot-y] "Needs to be disposed.")
  (set-cursor! [_ cursor])

  (->color [_ r g b a]))

(defprotocol Input
  (button-pressed?      [_ button])
  (button-just-pressed? [_ button])
  (key-pressed?      [_ k])
  (key-just-pressed? [_ k]))

(defprotocol TrueTypeFontGenerator
  (generate-ttf [_ {:keys [file size]}]))

(defprotocol ImageCreator
  (create-image [_ file])
  (get-scaled-copy [_ image scale]
                   "Scaled of original texture-dimensions, not any existing scale.")
  (get-sub-image [_ {:keys [file sub-image-bounds] :as image}]
                 "Coordinates are from original image, not scaled one.")
  (spritesheet [_ file tilew tileh])
  (get-sprite [_ {:keys [tilew tileh] :as sheet} [x y]]))

(defprotocol SoundStore
  (play-sound! [_ file]
               "Sound is already loaded from file, this will perform only a lookup for the sound and play it." ))

(defprotocol Stage
  (->stage-screen [_ {:keys [stage sub-screen]}]
                  "A screen with a stage as an input-processor which gets drawn and 'act'ed after the given sub-screen.
                  The stage will get disposed also.
                  Sub-screen is optional.")
  (get-stage [_] "Stage implements clojure.lang.ILookup (get) on actor id.")
  (mouse-on-stage-actor? [_])
  (add-to-stage! [_ actor]))

(defprotocol Widgets
  (->actor [_ {:keys [draw act]}])
  (->group [_ {:keys [actors] :as opts}])
  (->text-button [_ text on-clicked])
  (->check-box [_ text on-clicked checked?] "on-clicked is a fn of one arg, taking the current isChecked state")
  (->select-box [_ {:keys [items selected]}])
  (->image-button [_ image on-clicked]
                  [_ image on-clicked {:keys [dimensions]}])
  (->table [_ opts])
  (->window [_ {:keys [title modal? close-button? center?] :as opts}])
  (->label [_ text])
  (->text-field [_ text opts])
  (->split-pane [_ {:keys [first-widget
                           second-widget
                           vertical?] :as opts}])
  (->stack [_ actors])
  (->image-widget [_ object opts] "Takes either an image or drawable. Opts are :scaling, :align and actor opts.")
  (->texture-region-drawable [_ texture-region])
  (->horizontal-group [_ {:keys [space pad]}])
  (->vertical-group [_ actors])
  (->button-group [_ {:keys [max-check-count min-check-count]}])
  (->scroll-pane [_ actor]))

(defprotocol TiledMapLoader
  (->tiled-map [_ file] "Needs to be disposed."))

(defprotocol Assets
  (cached-texture [_ file])
  (all-sound-files   [_])
  (all-texture-files [_]))
