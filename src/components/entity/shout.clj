(ns components.entity.shout
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.world.grid :as grid]))

(def ^:private shout-radius 3)

; why in line-of-sight?
; sounds don't go through walls ??
; also not triggering for any other fighting sounds
; or remove totally sleeping ?

; gets itself also
; == faction/friendly? e1 e2 ( entity*/friendly? e*1 e*2) ?
(defn- friendlies-in-los [ctx entity*]
  (->> {:position (:position entity*)
        :radius shout-radius}
       (grid/circle->entities (ctx/world-grid ctx))
       (map deref)
       (filter #(and (= (:entity/faction %) (:entity/faction entity*))
                     (ctx/line-of-sight? ctx entity* %)))
       (map :entity/id)))

(defcomponent :entity/shout
  (component/tick [[_ counter] entity ctx]
    (when (ctx/stopped? ctx counter)
      (cons [:tx/destroy entity]
            (for [eid (friendlies-in-los ctx @entity)]
              [:tx/event eid :alert])))))

; this is actually an entity without body ... just delay do after x...
(defcomponent :tx/shout
  (component/do! [[_ position faction delay-seconds] ctx]
    [[:tx/create
      position
      {:width 0.5
       :height 0.5
       :z-order :z-order/effect}
      {:entity/faction faction ; causes potential field? FIXME
       :entity/shout (ctx/->counter ctx delay-seconds)}]]))
