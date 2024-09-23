(ns core.widgets.player-message
  (:require [core.component :as component :refer [defcomponent]]
            [core.graphics :as g]
            [core.graphics.views :refer [gui-viewport-width gui-viewport-height]]
            [core.ui :as ui]
            [core.tx :as tx])
  (:import com.badlogic.gdx.Gdx))

(def ^:private this :context/msg-to-player)

(defcomponent :tx/msg-to-player
  (tx/do! [[_ message] ctx]
    (assoc ctx this {:message message :counter 0})))

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (this ctx)]
    (g/draw-text g {:x (/ (gui-viewport-width ctx) 2)
                    :y (+ (/ (gui-viewport-height ctx) 2) 200)
                    :text message
                    :scale 2.5
                    :up? true})))

(defn- check-remove-message [{:keys [context/state] :as ctx}]
  (when-let [{:keys [counter]} (this ctx)]
    (swap! state update this update :counter + (.getDeltaTime Gdx/graphics))
    (when (>= counter duration-seconds)
      (swap! state assoc this nil))))

(defcomponent :widgets/player-message
  (component/create [_ ctx]
    (ui/->actor ctx {:draw draw-player-message
                     :act check-remove-message})))
