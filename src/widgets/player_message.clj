(ns widgets.player-message
  (:require [api.context :as ctx :refer [->actor delta-time]]
            [api.graphics :as g]
            [api.tx :refer [transact!]]))

(defn- get-data [ctx]
  (:player-message (:context/game ctx)))

(defmethod transact! :tx/msg-to-player [[_ message] ctx]
  (let [player-message (get-data ctx)]
    (reset! player-message {:message message :counter 0}))
  nil)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (let [player-message (get-data ctx)]
    (when-let [{:keys [message]} @player-message]
      (g/draw-text g {:x (/ (ctx/gui-viewport-width ctx) 2)
                      :y (+ (/ (ctx/gui-viewport-height ctx) 2) 200)
                      :text message
                      :scale 2.5
                      :up? true}))))

(defn- check-remove-message [ctx]
  (let [player-message (get-data ctx)]
    (when-let [{:keys [counter]} @player-message]
      (swap! player-message update :counter + (delta-time ctx))
      (when (>= counter duration-seconds)
        (reset! player-message nil)))))

(defn ->build [ctx]
  (->actor ctx {:draw draw-player-message
                :act check-remove-message}))

(defn ->data [_ctx]
  (atom nil))
