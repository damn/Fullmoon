(ns world.ecs
  (:require [clj-commons.pretty.repl :as p]
            [utils.core :refer [safe-merge sort-by-order]]
            [gdx.graphics.color :as color]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.graphics :as g]
            [core.entity :as entity :refer [map->Entity]]
            [core.effect :as effect]))

(def ^:private this :world/ecs)

(defcomponent this {}
  (component/create [_ _ctx]
    {}))

(defn- entities [ctx] (this ctx))

(defcomponent :entity/uid {}
  uid
  (entity/create [_ entity ctx]
    (assert (number? uid))
    (update ctx this assoc uid entity))

  (entity/destroy [_ _entity ctx]
    (assert (contains? (entities ctx) uid))
    (update ctx this dissoc uid)))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defcomponent :entity/id {}
  (entity/create  [[_ id] _eid _ctx] [[:tx/add-to-world      id]])
  (entity/destroy [[_ id] _eid _ctx] [[:tx/remove-from-world id]]))

(defcomponent :tx/create {}
  (effect/do! [[_ body components] ctx]
    (assert (and (not (contains? components :entity/id))
                 (not (contains? components :entity/uid))))
    (let [entity (atom nil)
          body (entity/->Body body)
          components (-> components
                         (assoc :entity/id entity
                                :entity/uid (unique-number!))
                         (component/update-map component/create ctx))
          entity* (safe-merge body components)]
      (reset! entity entity*)
      [(fn [ctx]
         (for [component @entity]
           (fn [ctx]
             ; we are assuming components dont remove other ones at entity/create
             ; thats why we reuse component and not fetch each time again for key
             (entity/create component entity ctx))))])))

(defcomponent :tx/destroy {}
  (effect/do! [[_ entity] ctx]
    [[:tx.entity/assoc entity :entity/destroyed? true]]))

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [g entity* color]
  (let [[x y] (:left-bottom entity*)]
    (g/draw-rectangle g x y (:width entity*) (:height entity*) color)))

(defn- render-entity* [system entity* g ctx]
  (try
   (when show-body-bounds
     (draw-body-rect g entity* (if (:collides? entity*) color/white color/gray)))
   (dorun (component/apply-system system entity* g ctx))
   (catch Throwable t
     (draw-body-rect g entity* color/red)
     (p/pretty-pst t 12))))

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

(extend-type core.context.Context
  core.context/EntityComponentSystem
  (all-entities [ctx]
    (vals (entities ctx)))

  (get-entity [ctx uid]
    (get (entities ctx) uid))

  (tick-entities! [ctx entities]
    (reduce tick-system ctx entities))

  (render-entities! [context g entities*]
    (doseq [entities* (map second
                           (sort-by-order (group-by :z-order entities*)
                                          first
                                          entity/render-order))
            system entity/render-systems
            entity* entities*]
      (render-entity* system entity* g context)))

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
