(ns ^:no-doc world.entity.animation
  (:require [component.core :refer [defc]]
            [component.db :as db]
            [component.schema :as schema]
            [gdx.graphics :as g]
            [world.core :as world]
            [world.entity :as entity]))

(defprotocol Animation
  (anim-tick [_ delta])
  (restart [_])
  (anim-stopped? [_])
  (current-frame [_]))

(defrecord ImmutableAnimation [frames frame-duration looping? cnt maxcnt]
  Animation
  (anim-tick [this delta]
    (let [maxcnt (float maxcnt)
          newcnt (+ (float cnt) (float delta))]
      (assoc this :cnt (cond (< newcnt maxcnt) newcnt
                             looping? (min maxcnt (- newcnt maxcnt))
                             :else maxcnt))))

  (restart [this]
    (assoc this :cnt 0))

  (anim-stopped? [_]
    (and (not looping?) (>= cnt maxcnt)))

  (current-frame [this]
    (frames (min (int (/ (float cnt) (float frame-duration)))
                 (dec (count frames))))))

(defn- ->animation [frames & {:keys [frame-duration looping?]}]
  (map->ImmutableAnimation
    {:frames (vec frames)
     :frame-duration frame-duration
     :looping? looping?
     :cnt 0
     :maxcnt (* (count frames) (float frame-duration))}))

(defn- edn->animation [{:keys [frames frame-duration looping?]}]
  (->animation (map g/edn->image frames)
               :frame-duration frame-duration
               :looping? looping?))

(defmethod db/edn->value :s/animation [_ animation]
  (edn->animation animation))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defmethod schema/form :s/animation [_]
  [:map {:closed true}
   [:frames :some] ; FIXME actually images
   [:frame-duration pos?]
   [:looping? :boolean]])

(defc :entity/animation
  {:schema :s/animation
   :let animation}
  (entity/create [_ eid]
    [(tx-assoc-image-current-frame eid animation)])

  (entity/tick [[k _] eid]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (anim-tick animation world/delta-time)]]))

(defc :entity/delete-after-animation-stopped?
  (entity/create [_ eid]
    (-> @eid :entity/animation :looping? not assert))

  (entity/tick [_ eid]
    (when (anim-stopped? (:entity/animation @eid))
      [[:e/destroy eid]])))
