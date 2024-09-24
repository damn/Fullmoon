(ns ^:no-doc core.widgets.player-message
  (:require [core.ctx :refer :all]
            [core.ctx.ui :as ui]))

(def ^:private this :context/msg-to-player)

(defcomponent :tx/msg-to-player
  (do! [[_ message] ctx]
    (assoc ctx this {:message message :counter 0})))

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (this ctx)]
    (draw-text g {:x (/ (gui-viewport-width ctx) 2)
                  :y (+ (/ (gui-viewport-height ctx) 2) 200)
                  :text message
                  :scale 2.5
                  :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (this ctx)]
    (swap! app-state update this update :counter + (.getDeltaTime gdx-graphics))
    (when (>= counter duration-seconds)
      (swap! app-state assoc this nil))))

(defcomponent :widgets/player-message
  (->mk [_ _ctx]
    (ui/->actor {:draw draw-player-message
                 :act check-remove-message})))
