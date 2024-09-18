(ns components.entity.animation
  (:require [gdx.scene2d.ui :as ui]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            [core.image :as image]))

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

(defmethod data/edn->value :data/animation [_ animation ctx]
  (edn->animation animation ctx))

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod data/->widget :data/animation [_ animation ctx]
  (ui/->table {:rows [(for [image (:frames animation)]
                        (ui/->image-widget (image/edn->image image ctx) {}))]
               :cell-defaults {:pad 1}}))

(defn- tx-assoc-image-current-frame [eid animation]
  [:tx/assoc eid :entity/image (current-frame animation)])

(defcomponent :entity/animation
  {:data :data/animation
   :let animation}
  (component/create-e [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (component/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:tx/assoc eid k (tick animation (ctx/delta-time ctx))]]))

(defcomponent :entity/delete-after-animation-stopped?
  (component/create-e [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (component/tick [_ entity _ctx]
    (when (stopped? (:entity/animation @entity))
      [[:tx/destroy entity]])))
