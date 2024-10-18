(ns world.entity.hpbar
  (:require [gdx.graphics :as g]))

(def ^:private hpbar-colors
  {:green     [0 0.8 0]
   :darkgreen [0 0.5 0]
   :yellow    [0.5 0.5 0]
   :red       [0.5 0 0]})

(defn- hpbar-color [ratio]
  (let [ratio (float ratio)
        color (cond
                (> ratio 0.75) :green
                (> ratio 0.5)  :darkgreen
                (> ratio 0.25) :yellow
                :else          :red)]
    (color hpbar-colors)))

(def ^:private borders-px 1)

(defn draw [{:keys [position width half-width half-height]}
            ratio]
  (let [[x y] position]
    (let [x (- x half-width)
          y (+ y half-height)
          height (g/pixels->world-units 5)
          border (g/pixels->world-units borders-px)]
      (g/draw-filled-rectangle x y width height :black)
      (g/draw-filled-rectangle (+ x border)
                               (+ y border)
                               (- (* width ratio) (* 2 border))
                               (- height (* 2 border))
                               (hpbar-color ratio)))))
