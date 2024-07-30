(ns cdq.entity.mouseover
  (:require [core.component :as component]
            [gdl.graphics :as g]
            [cdq.api.entity :as entity]))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(component/def :entity/mouseover? {}
  _
  (entity/render-below [_
                        {:keys [entity/position entity/body entity/faction]}
                        g
                        {:keys [context/player-entity] :as ctx}]
    (g/with-shape-line-width g 3
      #(g/draw-ellipse g
                       position
                       (:half-width body)
                       (:half-height body)
                       (cond (= faction (entity/enemy-faction @player-entity))
                             enemy-color
                             (= faction (entity/friendly-faction @player-entity))
                             friendly-color
                             :else
                             neutral-color)))))
