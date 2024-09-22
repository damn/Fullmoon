(ns core.entity.mouseover
  (:require [core.component :refer [defcomponent]]
            [core.graphics :as g]
            [core.entity :as entity]
            [core.entity.player :as player]))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defcomponent :entity/mouseover?
  (entity/render-below [_ {:keys [entity/faction] :as entity*} g ctx]
    (let [player-entity* (player/entity* ctx)]
      (g/with-shape-line-width g 3
        #(g/draw-ellipse g
                         (:position entity*)
                         (:half-width entity*)
                         (:half-height entity*)
                         (cond (= faction (entity/enemy-faction player-entity*))
                               enemy-color
                               (= faction (entity/friendly-faction player-entity*))
                               friendly-color
                               :else
                               neutral-color))))))
