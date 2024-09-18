(ns components.widgets.player-message
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.graphics :as g]
            [gdx.scene2d.ui :as ui]
            [core.tx :as tx])
  (:import com.badlogic.gdx.Gdx))

(defcomponent :tx/msg-to-player
  (tx/do! [[_ message] ctx]
    (assoc ctx ::msg {:message message :counter 0})))

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (::msg ctx)]
    (g/draw-text g {:x (/ (ctx/gui-viewport-width ctx) 2)
                    :y (+ (/ (ctx/gui-viewport-height ctx) 2) 200)
                    :text message
                    :scale 2.5
                    :up? true})))

(defn- check-remove-message [{:keys [context/state] :as ctx}]
  (when-let [{:keys [counter]} (::msg ctx)]
    (swap! state update ::msg update :counter + (.getDeltaTime Gdx/graphics))
    (when (>= counter duration-seconds)
      (swap! state assoc ::msg nil))))

(defcomponent :widgets/player-message
  (component/create [_ ctx]
    (ui/->actor ctx {:draw draw-player-message
                     :act check-remove-message})))
