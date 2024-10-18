(ns app.screens.map-editor
  (:require [clojure.string :as str]
            [component.db :as db]
            [gdx.graphics :as g :refer [white]]
            [gdx.graphics.camera :as 🎥]
            [gdx.input :refer [key-pressed? key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.error-window :refer [error-window!]]
            [gdx.ui.stage-screen :as stage-screen]
            [gdx.screen :as screen]
            [gdx.tiled :as t]
            [gdx.utils :refer [dispose!]]
            [level.generate :refer [generate-level]]
            [level.modules :refer [module-width module-height]]
            [level.tiled :refer [movement-properties movement-property]]))

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (🎥/set-position! camera
                    [(/ (t/width  tiled-map) 2)
                     (/ (t/height tiled-map) 2)])
  (🎥/set-zoom! camera
                (🎥/calculate-zoom camera
                                   :left [0 0]
                                   :top [0 (t/height tiled-map)]
                                   :right [(t/width tiled-map) 0]
                                   :bottom [0 0])))

(defn- current-data []
  (-> (screen/current)
      :sub-screen
      :current-data))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- map-infos ^String []
  (let [tile (mapv int (g/world-mouse-position))
        {:keys [tiled-map
                area-level-grid]} @(current-data)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (g/world-mouse-position)
                                 [module-width module-height])))
          (when area-level-grid
            (str "Creature id: " (t/property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (movement-property tiled-map tile) "\n"
               (apply vector (movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

(defn- ->info-window []
  (let [label (ui/label "")
        window (ui/window {:title "Info" :rows [[label]]})]
    (ui/add-actor! window (ui/actor {:act #(do
                                            (.setText label (map-infos))
                                            (.pack window))}))
    (a/set-position! window 0 (g/gui-viewport-height))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (🎥/set-zoom! camera (max 0.1 (+ (🎥/zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [camera]
  (when (key-pressed? :keys/shift-left)
    (adjust-zoom camera    zoom-speed))
  (when (key-pressed? :keys/minus)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (🎥/set-position! camera
                                           (update (🎥/position camera)
                                                   idx
                                                   #(f % camera-movement-speed))))]
    (if (key-pressed? :keys/left)  (apply-position 0 -))
    (if (key-pressed? :keys/right) (apply-position 0 +))
    (if (key-pressed? :keys/up)    (apply-position 1 +))
    (if (key-pressed? :keys/down)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map []
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data)
        visible-tiles (🎥/visible-tiles (g/world-camera))
        [x y] (mapv int (g/world-mouse-position))]
    (g/draw-rectangle x y 1 1 :white)
    (when start-position
      (g/draw-filled-rectangle (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [prop (movement-property tiled-map [x y])]]
        (g/draw-filled-circle [(+ x 0.5) (+ y 0.5)] 0.08 :black)
        (g/draw-filled-circle [(+ x 0.5) (+ y 0.5)]
                              0.05
                              (case prop
                                "all"   :green
                                "air"   :orange
                                "none"  :red))))
    (when show-grid-lines
      (g/draw-grid 0 0 (t/width  tiled-map) (t/height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate-screen-ctx [properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (generate-modules context properties)
        {:keys [tiled-map start-position]} (generate-level world-id)
        atom-data (current-data)]
    (dispose! (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (g/world-camera) tiled-map)
    (.setVisible (t/get-layer tiled-map "creatures") true)))

(defn ->generate-map-window [level-id]
  (ui/window {:title "Properties"
              :cell-defaults {:pad 10}
              :rows [[(ui/label (with-out-str
                                (clojure.pprint/pprint
                                 (db/get level-id))))]
                     [(ui/text-button "Generate" #(try (generate-screen-ctx (db/get level-id))
                                                       (catch Throwable t
                                                         (error-window! t)
                                                         (println t))))]]
              :pack? true}))

(defrecord MapEditorScreen [current-data]
  screen/Screen
  (screen/enter! [_]
    (show-whole-map! (g/world-camera) (:tiled-map @current-data)))

  (screen/exit! [_]
    (🎥/reset-zoom! (g/world-camera)))

  (screen/render! [_]
    (g/draw-tiled-map (:tiled-map @current-data) (constantly white))
    (g/render-world-view! render-on-map)
    (if (key-just-pressed? :keys/l)
      (swap! current-data update :show-grid-lines not))
    (if (key-just-pressed? :keys/m)
      (swap! current-data update :show-movement-properties not))
    (camera-controls (g/world-camera))
    (when (key-just-pressed? :keys/escape)
      (screen/change! :screens/main-menu)))

  (dispose! [_]
    (dispose! (:tiled-map @current-data))))

(defn create []
  [:screens/map-editor
   (stage-screen/create :actors [(->generate-map-window world-id)
                                 (->info-window)]
                        :screen (->MapEditorScreen (atom {:tiled-map (t/load-map level.generate/modules-file)
                                                          :show-movement-properties false
                                                          :show-grid-lines false})))])
