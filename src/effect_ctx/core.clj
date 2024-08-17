(ns effect-ctx.core
  (:require [clojure.string :as str]
            [api.effect :as effect]))

; TODO
; these are actually functions on 'effect' itself as map
; (set of effect-components)
; ...
; TODO effect/text & effect/usable? could be on effect itself as map of effects
; whhile api.effect-component could be a separate API?
; or usable itself is recursive ? like text ?

(defn text [effect-ctx effects]
  (str/join "\n" (keep #(effect/text % effect-ctx) effects)))

(defn usable? [effect-ctx effect]
  (every? #(effect/usable? % effect-ctx) effect))
