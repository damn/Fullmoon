(ns components.widgets.player-message
  (:require [gdx.graphics :as graphics]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx :refer [->actor]]
            [core.graphics :as g]))

(defcomponent :tx/msg-to-player
  (component/do! [[_ message] ctx]
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
    (swap! state update ::msg update :counter + (graphics/delta-time))
    (when (>= counter duration-seconds)
      (swap! state assoc ::msg nil))))

(defcomponent :widgets/player-message
  (component/create [_ ctx]
    (->actor ctx {:draw draw-player-message
                  :act check-remove-message})))
