(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.context :as ctx]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

(defn valid-params? [effect-ctx effect]
  (every? #(effect/valid-params? % effect-ctx) effect))

(defn text [effect-ctx effect]
  (str/join "\n" (keep #(effect/text % effect-ctx) effect)))

(defn useful? [effect-ctx effect ctx]
  (some #(effect/useful? % effect-ctx ctx) effect))

(defn render-info [effect-ctx g effect]
  (run! #(effect/render-info % effect-ctx g) effect))

(defmethod transact! :tx/effect [[_ effect-ctx effect] ctx]
  (-> ctx
      (merge effect-ctx)
      (ctx/transact-all! effect)
      (dissoc :effect/source
              :effect/target
              :effect/direction
              :effect/target-position)))

