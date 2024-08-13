(ns widgets.player-message
  (:require [clj.gdx.graphics :as graphics]
            [api.context :as ctx :refer [->actor]]
            [api.graphics :as g]
            [api.tx :refer [transact!]]
            app.state))

(defn- player-message [ctx]
  (:context.game/player-message ctx))

(defmethod transact! :tx/msg-to-player [[_ message] ctx]
  (assoc ctx :context.game/player-message {:message message :counter 0}))

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (player-message ctx)]
    (g/draw-text g {:x (/ (ctx/gui-viewport-width ctx) 2)
                    :y (+ (/ (ctx/gui-viewport-height ctx) 2) 200)
                    :text message
                    :scale 2.5
                    :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (player-message ctx)]
    (swap! app.state/current-context update :context.game/player-message update :counter + (graphics/delta-time ctx))
    (when (>= counter duration-seconds)
      (swap! app.state/current-context assoc :context.game/player-message nil))))

(defn ->build [ctx]
  (->actor ctx {:draw draw-player-message
                :act check-remove-message}))
