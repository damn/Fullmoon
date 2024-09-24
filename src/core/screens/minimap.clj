(ns ^:no-doc core.screens.minimap
  (:require [data.grid2d :as grid2d]
            [core.graphics.camera :as camera]
            [core.ctx :refer :all]
            [core.screens :as screens]
            [core.tiled :as tiled])
  (:import com.badlogic.gdx.Input$Keys
           com.badlogic.gdx.graphics.Color))

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(defn- calculate-zoom [{:keys [context/explored-tile-corners] :as ctx}]
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @explored-tile-corners)))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (camera/calculate-zoom (world-camera ctx)
                           :left left
                           :top top
                           :right right
                           :bottom bottom)))

(defn- ->tile-corner-color-setter [explored?]
  (fn tile-corner-color-setter [color x y]
    (if (get explored? [x y])
      Color/WHITE
      Color/BLACK)))

#_(deftype Screen []
    (show [_ ctx]
      (camera/set-zoom! (world-camera ctx) (calculate-zoom ctx)))

    (hide [_ ctx]
      (camera/reset-zoom! (world-camera ctx)))

    ; TODO fixme not subscreen
    (render [_ {:keys [context/tiled-map context/explored-tile-corners] :as context}]
      (tiled/render! context
                     tiled-map
                     (->tile-corner-color-setter @explored-tile-corners))
      (render-world-view context
                         (fn [g]
                           (draw-filled-circle g
                                               (camera/position (world-camera context))
                                               0.5
                                               Color/GREEN)))
      (if (or (.isKeyJustPressed gdx-input Input$Keys/TAB)
              (.isKeyJustPressed gdx-input Input$Keys/ESCAPE))
        (screens/change-screen context :screens/world)
        context)))

#_(defcomponent :screens/minimap
  (->mk [_ _ctx]
    (->Screen)))
