(ns widgets.player-message
  (:require [api.context :as ctx :refer [->actor]]
            [api.graphics :as g]
            [api.tx :refer [transact!]]))

(defn- player-message [ctx]
  (:player-message @(:context/game ctx)))

(defmethod transact! :tx/msg-to-player [[_ message] ctx]
  (swap! (:context/game ctx) assoc :player-message {:message message :counter 0})
  ctx)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (player-message ctx)]
    (g/draw-text g {:x (/ (ctx/gui-viewport-width ctx) 2)
                    :y (+ (/ (ctx/gui-viewport-height ctx) 2) 200)
                    :text message
                    :scale 2.5
                    :up? true})))

(defn- check-remove-message [ctx]
  (let [game-state (:context/game ctx)]
    (when-let [{:keys [counter]} (:player-message @game-state)]
      (swap! game-state update :player-message update :counter + (ctx/delta-time-raw ctx))
      (when (>= counter duration-seconds)
        (swap! game-state assoc :player-message nil)))))

(defn ->build [ctx]
  (->actor ctx {:draw draw-player-message
                :act check-remove-message}))

(defn ->data [_ctx]
  nil)
