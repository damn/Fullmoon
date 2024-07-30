(ns cdq.tx.target-entity
  (:require [core.component :as component]
            [gdl.graphics :as g]
            [gdl.math.vector :as v]
            [cdq.api.context :refer [transact! effect-text line-of-sight? line-entity]]
            [cdq.api.effect :as effect]
            [cdq.api.entity :as entity]
            [cdq.attributes :as attr]))

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

(component/def :maxrange attr/pos-attr)

(component/def :tx/target-entity {:widget :nested-map ; TODO circular depdenency components-attribute  - cannot use map-attribute..
                                  :schema [:map {:closed true}
                                           [:hit-effect [:map]]
                                           [:maxrange pos?]]
                                  :default-value {:hit-effect {}
                                                  :max-range 2.0}}
  {:keys [maxrange hit-effect]}
  (effect/text [_ ctx]
               (str "Range " maxrange " meters\n" (effect-text ctx hit-effect)))

  ; TODO lOs move to effect/target effect-context creation?

  ; TODO target still exists ?! necessary ? what if disappears/dead?
  ; TODO (:entity/hp @target) is valid-params of hit-effect damage !! -> allow anyway and just do nothing then?
  (effect/valid-params? [_ {:keys [effect/source effect/target] :as ctx}]
                        (and source
                             target
                             (line-of-sight? ctx @source @target)
                             (:entity/hp @target)))

  (effect/useful? [_ {:keys [effect/source effect/target]}]
                  (in-range? @source @target maxrange))

  (transact! [_ {:keys [effect/source effect/target] :as ctx}]
             (let [source* @source
                   target* @target]
               (if (in-range? source* target* maxrange)
                 [[:tx/create (line-entity ctx
                                           {:start (start-point source* target*)
                                            :end (:entity/position target*)
                                            :duration 0.05
                                            :color [1 0 0 0.75]
                                            :thick? true})]
                  ; TODO => make new context with end-point ... and check on point entity
                  ; friendly fire ?!
                  ; player maybe just direction possible ?!
                  [:tx/effect ctx hit-effect]]
                 [; TODO
                  ; * clicking on far away monster
                  ; * hitting ground in front of you ( there is another monster )
                  ; * -> it doesn't get hit ! hmmm
                  ; * either use 'MISS' or get enemy entities at end-point
                  [:tx/audiovisual (end-point source* target* maxrange) :effects.target-entity/hit-ground-effect]])))

  (effect/render-info [_ g {:keys [effect/source effect/target] :as ctx}]
                      (let [source* @source
                            target* @target]
                        (g/draw-line g
                                     (start-point source* target*)
                                     (end-point   source* target* maxrange)
                                     (if (in-range? source* target* maxrange)
                                       [1 0 0 0.5]
                                       [1 1 0 0.5])))))
