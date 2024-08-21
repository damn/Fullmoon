(ns widgets.player-message
  (:require [gdx.graphics :as graphics]
            [core.component :refer [defcomponent]]
            [api.context :as ctx :refer [->actor]]
            [api.graphics :as g]
            [api.effect :as effect]
            app))

(defcomponent :tx/msg-to-player {}
  (effect/do! [[_ message] ctx]
    (assoc ctx ::msg {:message message :counter 0})))

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (::msg ctx)]
    (g/draw-text g {:x (/ (ctx/gui-viewport-width ctx) 2)
                    :y (+ (/ (ctx/gui-viewport-height ctx) 2) 200)
                    :text message
                    :scale 2.5
                    :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (::msg ctx)]
    (swap! app/state update ::msg update :counter + (graphics/delta-time))
    (when (>= counter duration-seconds)
      (swap! app/state assoc ::msg nil))))

(defn ->build [ctx]
  (->actor ctx {:draw draw-player-message
                :act check-remove-message}))
