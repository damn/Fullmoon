(ns core.screens.map-editor
  (:require [clojure.string :as str]
            [gdx.graphics.camera :as camera]
            [gdx.maps.tiled :as tiled]
            [core.component :refer [defcomponent] :as component]
            [utils.core :refer [->tile]]
            [core.tiled-map-renderer :as tiled-map-renderer]
            [core.screens :as screens]
            [core.g :as g]
            [core.graphics :as graphics]
            [core.graphics.views :refer [world-mouse-position gui-viewport-height world-camera]]
            [core.screen :as screen]
            [core.screens.stage :as stage]
            [core.property :as property]
            [core.widgets.error-modal :refer [error-window!]]
            [gdx.scene2d.actor :refer [set-position!]]
            [gdx.scene2d.group :refer [add-actor!]]
            [gdx.scene2d.ui :as ui]
            [core.property.types.world :as level-generator]
            mapgen.gen
            mapgen.modules)
  (:import (com.badlogic.gdx Gdx Input$Keys)
           com.badlogic.gdx.graphics.Color
           com.badlogic.gdx.utils.Disposable))

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (camera/set-position! camera
                        [(/ (tiled/width  tiled-map) 2)
                         (/ (tiled/height tiled-map) 2)])
  (camera/set-zoom! camera
                    (camera/calculate-zoom camera
                                           :left [0 0]
                                           :top [0 (tiled/height tiled-map)]
                                           :right [(tiled/width tiled-map) 0]
                                           :bottom [0 0])))

(defn- current-data [ctx]
  (-> ctx
      screens/current-screen
      (get 1)
      :sub-screen
      (get 1)))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- debug-infos ^String [ctx]
  (let [tile (->tile (world-mouse-position ctx))
        {:keys [tiled-map
                area-level-grid]} @(current-data ctx)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (world-mouse-position ctx)
                                 [mapgen.modules/module-width
                                  mapgen.modules/module-height])))
          (when area-level-grid
            (str "Creature id: " (tiled/property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (tiled/movement-property tiled-map tile) "\n"
               (apply vector (tiled/movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window [ctx]
  (let [label (ui/->label "")
        window (ui/->window {:title "Info" :rows [[label]]})]
    (add-actor! window (ui/->actor ctx {:act #(do
                                               (.setText label (debug-infos %))
                                               (.pack window))}))
    (set-position! window 0 (gui-viewport-height ctx))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [context camera]
  (when (.isKeyPressed Gdx/input Input$Keys/SHIFT_LEFT)
    (adjust-zoom camera    zoom-speed))
  (when (.isKeyPressed Gdx/input Input$Keys/MINUS)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (camera/set-position! camera
                                               (update (camera/position camera)
                                                       idx
                                                       #(f % camera-movement-speed))))]
    (if (.isKeyPressed Gdx/input Input$Keys/LEFT)  (apply-position 0 -))
    (if (.isKeyPressed Gdx/input Input$Keys/RIGHT) (apply-position 0 +))
    (if (.isKeyPressed Gdx/input Input$Keys/UP)    (apply-position 1 +))
    (if (.isKeyPressed Gdx/input Input$Keys/DOWN)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map [g ctx]
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data ctx)
        visible-tiles (camera/visible-tiles (world-camera ctx))
        [x y] (->tile (world-mouse-position ctx))]
    (g/draw-rectangle g x y 1 1 Color/WHITE)
    (when start-position
      (g/draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (tiled/movement-property tiled-map [x y])]]
        (g/draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                              0.08
                              Color/BLACK)
        (g/draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                              0.05
                              (case movement-property
                                "all"   Color/GREEN
                                "air"   Color/ORANGE
                                "none"  Color/RED))))
    (when show-grid-lines
      (g/draw-grid g 0 0 (tiled/width  tiled-map) (tiled/height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate [context properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (mapgen.gen/generate context properties)
        {:keys [tiled-map start-position]} (level-generator/->world context world-id)
        atom-data (current-data context)]
    (.dispose ^Disposable (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (world-camera context) tiled-map)
    (.setVisible (tiled/get-layer tiled-map "creatures") true)
    context))

(defn ->generate-map-window [ctx level-id]
  (ui/->window {:title "Properties"
                :cell-defaults {:pad 10}
                :rows [[(ui/->label (with-out-str
                                     (clojure.pprint/pprint
                                      (property/build ctx level-id))))]
                       [(ui/->text-button ctx "Generate" #(try (generate % (property/build % level-id))
                                                               (catch Throwable t
                                                                 (error-window! % t)
                                                                 (println t)
                                                                 %)))]]
                :pack? true}))

(defcomponent ::sub-screen
  {:let current-data}
  ; TODO ?
  ;com.badlogic.gdx.utils.Disposable
  #_(dispose [_]
      (dispose (:tiled-map @current-data)))

  (screen/enter [_ ctx]
    (show-whole-map! (world-camera ctx) (:tiled-map @current-data)))

  (screen/exit [_ ctx]
    (camera/reset-zoom! (world-camera ctx)))

  (screen/render [_ context]
    (tiled-map-renderer/render! context
                                (:tiled-map @current-data)
                                (constantly Color/WHITE))
    (graphics/render-world-view context #(render-on-map % context))
    (if (.isKeyJustPressed Gdx/input Input$Keys/L)
      (swap! current-data update :show-grid-lines not))
    (if (.isKeyJustPressed Gdx/input Input$Keys/M)
      (swap! current-data update :show-movement-properties not))
    (camera-controls context (world-camera context))
    (if (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
      (screens/change-screen context :screens/main-menu)
      context)))

(derive :screens/map-editor :screens/stage)
(defcomponent :screens/map-editor
  (component/create [_ ctx]
    {:sub-screen [::sub-screen
                  (atom {:tiled-map (tiled/load-map mapgen.modules/modules-file)
                         :show-movement-properties false
                         :show-grid-lines false})]
     :stage (stage/create ctx [(->generate-map-window ctx world-id)
                              (->info-window ctx)])}))
