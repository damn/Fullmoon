(ns entity.hp
  (:require [core.component :as component]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [data.val-max :refer [val-max-ratio]]
            [api.entity :as entity]
            [context.ui.config :refer (hpbar-height-px)]
            [data.types :as attr]))

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

; required for target-entity (remove)
(component/def :entity/hp attr/pos-int-attr
  hp
  (entity/create-component [[_ max-hp] _components _ctx]
    [max-hp max-hp])

  (entity/render-info [_
                       {[x y] :entity/position
                        {:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?]}
                       g
                       _ctx]
    (let [ratio (val-max-ratio hp)]
      (when (or (< ratio 1) mouseover?)
        (let [x (- x half-width)
              y (+ y half-height)
              height (g/pixels->world-units g hpbar-height-px) ; pre-calculate it maybe somehow, but will put too much stuff in properties?
              border (g/pixels->world-units g borders-px)] ; => can actually still use global state? idk
          (g/draw-filled-rectangle g x y width height color/black)
          (g/draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio)))))))
