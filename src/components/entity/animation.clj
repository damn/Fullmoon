(ns components.entity.animation
  (:require [core.component :as component :refer [defcomponent]]
            [core.animation :as animation]
            [core.context :as ctx]))

(defn- tx-assoc-image-current-frame [eid animation]
  [:tx.entity/assoc eid :entity/image (animation/current-frame animation)])

(defcomponent :entity/animation
  {:data :animation
   :optional? false
   :let animation}
  (component/edn->value [[_ {:keys [frames frame-duration looping?]}] ctx]
    (animation/create (map #(component/edn->value [:entity/image %] ctx) frames)
                      :frame-duration frame-duration
                      :looping? looping?))

  (component/value->edn [_]
    (-> animation
        (update :frames (fn [frames] (map #(component/value->edn [:entity/image %]) frames)))
        (select-keys [:frames :frame-duration :looping?])))

  (component/create-e [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (component/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:tx.entity/assoc eid k (animation/tick animation (ctx/delta-time ctx))]]))
