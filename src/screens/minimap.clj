(ns screens.minimap
  (:require [core.component :refer [defcomponent]]
            [app.state :refer [current-context change-screen!]]
            [api.context :as ctx :refer [key-just-pressed? explored?]]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.graphics.camera :as camera]
            [api.input.keys :as input.keys]
            [api.screen :as screen]))

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(defn- calculate-zoom [{:keys [context/world] :as ctx}]
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @(:explored-tile-corners world))))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (camera/calculate-zoom (ctx/world-camera ctx)
                           :left left
                           :top top
                           :right right
                           :bottom bottom)))

; TODO FIXME deref'fing current-context at each tile corner
; massive performance issue - probably
; => pass context through java tilemap render class
; or prepare colors before
(defn- tile-corner-color-setter [color x y]
  (if (explored? @current-context [x y])
    color/white
    color/black))

(deftype Screen []
  api.screen/Screen
  (show [_ ctx]
    (camera/set-zoom! (ctx/world-camera ctx) (calculate-zoom ctx)))

  (hide [_ ctx]
    (camera/reset-zoom! (ctx/world-camera ctx)))

  (render [_ {:keys [context/world] :as context}]
    (ctx/render-tiled-map context (:tiled-map world) tile-corner-color-setter)
    (ctx/render-world-view context
                           (fn [g]
                             (g/draw-filled-circle g
                                                   (camera/position (ctx/world-camera context))
                                                   0.5
                                                   color/green)))
    (when (or (key-just-pressed? context input.keys/tab)
              (key-just-pressed? context input.keys/escape))
      (change-screen! :screens/game))))

(defcomponent :screens/minimap {}
  (screen/create [_ _ctx] (->Screen)))
