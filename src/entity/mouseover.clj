(ns entity.mouseover
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.graphics :as g]
            [api.entity :as entity]))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defcomponent :entity/mouseover? {}
  (entity/render-below [_ {:keys [entity/position entity/body entity/faction]} g ctx]
    (let [player-entity* (ctx/player-entity* ctx)]
      (g/with-shape-line-width g 3
        #(g/draw-ellipse g
                         position
                         (:half-width body)
                         (:half-height body)
                         (cond (= faction (entity/enemy-faction player-entity*))
                               enemy-color
                               (= faction (entity/friendly-faction player-entity*))
                               friendly-color
                               :else
                               neutral-color))))))
