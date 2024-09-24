(ns ^:no-doc core.entity.animation
  (:require [core.ui :as ui]
            [core.ctx :refer :all]
            [core.ctx.property :as property]
            [core.entity :as entity]
            [core.graphics.image :as image]
            [core.ctx.time :as time]))

(defprotocol Animation
  (tick [_ delta])
  (restart [_])
  (stopped? [_])
  (current-frame [_]))

(defrecord ImmutableAnimation [frames frame-duration looping? cnt maxcnt]
  Animation
  (tick [this delta]
    (let [maxcnt (float maxcnt)
          newcnt (+ (float cnt) (float delta))]
      (assoc this :cnt (cond (< newcnt maxcnt) newcnt
                             looping? (min maxcnt (- newcnt maxcnt))
                             :else maxcnt))))

  (restart [this]
    (assoc this :cnt 0))

  (stopped? [_]
    (and (not looping?) (>= cnt maxcnt)))

  (current-frame [this]
    (frames (min (int (/ (float cnt) (float frame-duration)))
                 (dec (count frames))))))

(defn- create [frames & {:keys [frame-duration looping?]}]
  (map->ImmutableAnimation
    {:frames (vec frames)
     :frame-duration frame-duration
     :looping? looping?
     :cnt 0
     :maxcnt (* (count frames) (float frame-duration))}))

(defn- edn->animation [{:keys [frames frame-duration looping?]} ctx]
  (create (map #(image/edn->image % ctx) frames)
          :frame-duration frame-duration
          :looping? looping?))

(defcomponent :data/animation
  {:schema [:map {:closed true}
            [:frames :some]
            [:frame-duration pos?]
            [:looping? :boolean]]})

(defmethod property/edn->value :data/animation [_ animation ctx]
  (edn->animation animation ctx))

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod property/->widget :data/animation [_ animation ctx]
  (ui/->table {:rows [(for [image (:frames animation)]
                        (ui/->image-widget (image/edn->image image ctx) {}))]
               :cell-defaults {:pad 1}}))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defcomponent :entity/animation
  {:data :data/animation
   :let animation}
  (entity/create [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (entity/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (tick animation (time/delta-time ctx))]]))

(defcomponent :entity/delete-after-animation-stopped?
  (entity/create [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (entity/tick [_ entity _ctx]
    (when (stopped? (:entity/animation @entity))
      [[:e/destroy entity]])))
