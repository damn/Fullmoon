(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.effect :as effect]))

(defn text [effect-ctx effects]
  (str/join "\n" (keep #(effect/text % effect-ctx) effects)))

(defn applicable? [effect-ctx effects]
  (some #(effect/applicable? % effect-ctx) effects))
