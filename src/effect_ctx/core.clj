(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.effect :as effect]))

(defn text [effect-ctx effect]
  (str/join "\n" (keep #(effect/text % effect-ctx) effect)))
