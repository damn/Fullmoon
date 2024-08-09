(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.effect :as effect]))

(defn valid-params? [effect-ctx effect]
  (every? #(effect/valid-params? % effect-ctx) effect))

(defn text [effect-ctx effect]
  (str/join "\n" (keep #(effect/text % effect-ctx) effect)))

(defn useful? [effect-ctx effect ctx]
  (some #(effect/useful? % effect-ctx ctx) effect))

(defn txs [effect-ctx effect]
  (mapcat #(effect/txs % effect-ctx) effect))

(defn render-info [effect-ctx g effect]
  (run! #(effect/render-info % effect-ctx g) effect))
