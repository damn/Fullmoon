(ns effect.target-entity
  (:require [math.vector :as v]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.graphics :as g]
            [api.context :refer [line-of-sight?]]
            [api.effect :as effect]
            [api.entity :as entity]
            [effect-ctx.core :as effect-ctx]))

(defn- in-range? [entity* target* maxrange] ; == circle-collides?
  (< (- (float (v/distance (:entity/position entity*)
                           (:entity/position target*)))
        (float (:radius (:entity/body entity*)))
        (float (:radius (:entity/body target*))))
     (float maxrange)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity* target*]
  (v/add (:entity/position entity*)
         (v/scale (entity/direction entity* target*)
                  (:radius (:entity/body entity*)))))

(defn- end-point [entity* target* maxrange]
  (v/add (start-point entity* target*)
         (v/scale (entity/direction entity* target*)
                  maxrange)))

(defcomponent :maxrange data/pos-attr)
; TODO how should this work ???
; can not contain the other effects properly o.o
(defcomponent :hit-effect (data/components-attribute :effect))

(defcomponent :effect/target-entity {:widget :nested-map ; TODO circular depdenency components-attribute  - cannot use map-attribute..
                                     :schema [:map {:closed true}
                                              [:hit-effect [:map]]
                                              [:maxrange pos?]]
                                     :default-value {:hit-effect {}
                                                     :max-range 2.0}}
  (effect/text [[_ {:keys [maxrange hit-effect]}] effect-ctx]
    (str "Range " maxrange " meters\n" (effect-ctx/text effect-ctx hit-effect)))

  ; TODO lOs move to effect/target effect-context creation?
  ; TODO target still exists ?! necessary ? what if disappears/dead?
  ; TODO (:entity/hp @target) is valid-params of hit-effect damage !! -> allow anyway and just do nothing then?
  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source
         target
         ;(line-of-sight? ctx @source @target) ; TODO make it @ effect-context creation that only targets w. line of sight ...
         (:entity/hp @target)))

  (effect/useful? [[_ {:keys [maxrange]}] {:keys [effect/source effect/target]} _ctx]
    (in-range? @source @target maxrange))

  (effect/txs [[_ {:keys [maxrange hit-effect]}]
                {:keys [effect/source effect/target] :as effect-ctx}]
    (let [source* @source
          target* @target]
      (if (in-range? source* target* maxrange)
        (cons
         [:tx.entity/line-render {:start (start-point source* target*)
                                  :end (:entity/position target*)
                                  :duration 0.05
                                  :color [1 0 0 0.75]
                                  :thick? true}]
         ; TODO => make new context with end-point ... and check on point entity
         ; friendly fire ?!
         ; player maybe just direction possible ?!
         (effect-ctx/txs effect-ctx hit-effect))
        [; TODO
         ; * clicking on far away monster
         ; * hitting ground in front of you ( there is another monster )
         ; * -> it doesn't get hit ! hmmm
         ; * either use 'MISS' or get enemy entities at end-point
         [:tx.entity/audiovisual (end-point source* target* maxrange) :audiovisuals/hit-ground]])))

  (effect/render-info [[_ {:keys [maxrange]}] {:keys [effect/source effect/target]} g]
    (let [source* @source
          target* @target]
      (g/draw-line g
                   (start-point source* target*)
                   (end-point   source* target* maxrange)
                   (if (in-range? source* target* maxrange)
                     [1 0 0 0.5]
                     [1 1 0 0.5])))))
