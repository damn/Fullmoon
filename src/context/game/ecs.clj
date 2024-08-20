(ns context.game.ecs
  (:require [clj-commons.pretty.repl :as p]
            [utils.core :refer [sort-by-order]]
            [gdx.graphics.color :as color]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.graphics :as g]
            [api.entity :as entity :refer [map->Entity]]
            [api.effect :as effect]))

(defn ->build []
  {::entities {}})

(defcomponent :entity/uid {}
  (entity/create [[_ uid] entity ctx]
    {:pre [(number? uid)]}
    (update ctx ::entities assoc uid entity))

  (entity/destroy [[_ uid] _entity ctx]
    {:pre [(contains? (::entities ctx) uid)]}
    (update ctx ::entities dissoc uid)))

(defcomponent ::setup-entity {}
  (effect/do! [[_ entity uid components] ctx]
    {:pre [(not (contains? components :entity/id))
           (not (contains? components :entity/uid))]}
    (reset! entity (-> components
                       (assoc :entity/id entity :entity/uid uid)
                       (component/update-map component/create ctx)
                       map->Entity))
    ctx))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defcomponent :tx/create {}
  (effect/do! [[_ components] ctx]
    (let [entity (atom nil)]
      [[::setup-entity entity (unique-number!) components]
       (fn [ctx]
         (for [component @entity]
           (fn [ctx]
             ; we are assuming components dont remove other ones at entity/create
             ; thats why we reuse component and not fetch each time again for key
             (entity/create component entity ctx))))])))

(defcomponent :tx/destroy {}
  (effect/do! [[_ entity] ctx]
    [[:tx.entity/assoc entity :entity/destroyed? true]]))

(defn- draw-body-rect [g entity*]
  (let [[x y] (entity/left-bottom entity*)]
    (g/draw-rectangle g x y (entity/width entity*) (entity/height entity*) color/red)))

; TODO for creates lazy seqs
; how else to do it ?

(defn- render-entity* [system entity* g ctx]
  (try
   (dorun (component/apply-system system entity* g ctx))
   (catch Throwable t
     (draw-body-rect g entity*)
     (p/pretty-pst t 12)
     ; cannot pass it to main game context
     ; as render loop is not reducing over ctx
     #_(throw (ex-info "" (select-keys entity* [:entity/uid])))
     )))

(defn- tick-system [ctx entity]
  (try
   (reduce (fn [ctx k]
             ; precaution in case a component gets removed by another component
             ; the question is do we still want to update nil components ?
             ; should be contains? check ?
             (if-let [v (k @entity)]
               (let [component [k v]]
                 (ctx/do! ctx (entity/tick component entity ctx)))
               ctx))
           ctx
           (keys @entity))
   (catch Throwable t
     (throw (ex-info "" (select-keys @entity [:entity/uid]) t))
     ctx)))

(extend-type api.context.Context
  api.context/EntityComponentSystem
  (all-entities [ctx]
    (vals (::entities ctx)))

  (get-entity [ctx uid]
    (get (::entities ctx) uid))

  (tick-entities! [ctx entities]
    (reduce tick-system ctx entities))

  (render-entities! [context g entities*]
    (doseq [entities* (map second
                           (sort-by-order (group-by entity/z-order entities*)
                                          first
                                          entity/render-order))
            system entity/render-systems
            entity* entities*]
      (render-entity* system entity* g context))
    (doseq [entity* entities*]
      (render-entity* entity/render-debug entity* g context)))

  (remove-destroyed-entities! [ctx]
    (for [entity (filter (comp :entity/destroyed? deref) (ctx/all-entities ctx))
          component @entity]
      (fn [ctx]
        (entity/destroy component entity ctx)))))

(defcomponent :tx.entity/assoc {}
  (effect/do! [[_ entity k v] ctx]
    (assert (keyword? k))
    (swap! entity assoc k v)
    ctx))

(defcomponent :tx.entity/assoc-in {}
  (effect/do! [[_ entity ks v] ctx]
    (swap! entity assoc-in ks v)
    ctx))

(defcomponent :tx.entity/dissoc {}
  (effect/do! [[_ entity k] ctx]
    (assert (keyword? k))
    (swap! entity dissoc k)
    ctx))

(defcomponent :tx.entity/dissoc-in {}
  (effect/do! [[_ entity ks] ctx]
    (assert (> (count ks) 1))
    (swap! entity update-in (drop-last ks) dissoc (last ks))
    ctx))

(defcomponent :tx.entity/update-in {}
  (effect/do! [[_ entity ks f] ctx]
    (swap! entity update-in ks f)
    ctx))
