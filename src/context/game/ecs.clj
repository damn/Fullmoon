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
  {:context.game/uids-entities {}})

(defn- uids-entities [ctx]
  (:context.game/uids-entities ctx))

(defcomponent :entity/uid {}
  (entity/create [_ entity ctx]
    {:pre [(number? (:entity/uid @entity))]}
    (update ctx :context.game/uids-entities assoc (:entity/uid @entity) entity))

  (entity/destroy [[_ uid] _entity ctx]
    {:pre [(contains? (uids-entities ctx) uid)]}
    (update ctx :context.game/uids-entities dissoc uid)))

(defmethod effect/do! ::setup-entity [[_ entity uid components] ctx]
  {:pre [(not (contains? components :entity/id))
         (not (contains? components :entity/uid))]}
  (reset! entity (-> components
                     (assoc :entity/id entity :entity/uid uid)
                     (component/update-map entity/create-component components ctx)
                     map->Entity))
  ctx)

(defmethod effect/do! ::create-components [[_ entity] ctx]
  ; we are assuming components dont remove other ones at create
  ; so we fetch entity* for reduce once and not check if component still exists
  ; thats why no @entity and during the reduce-fn check if k is there
  (for [component @entity]
    (fn [ctx]
      (entity/create component entity ctx))))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defmethod effect/do! :tx/create [[_ components] ctx]
  (let [entity (atom nil)]
    [[::setup-entity entity (unique-number!) components]
     [::create-components entity]]))

(defmethod effect/do! :tx/destroy [[_ entity] ctx]
  [[:tx.entity/assoc entity :entity/destroyed? true]])

(defn- draw-body-rect [g entity*]
  (let [[x y] (entity/left-bottom entity*)]
    (g/draw-rectangle g x y (entity/width entity*) (entity/height entity*) color/red)))

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

(defn- do-components! [ctx system entity]
  (reduce (fn [ctx k]
            (if-let [v (k @entity)] ; precaution in case a component gets removed by another component
              (let [component [k v]]
               (ctx/do! ctx (system component entity ctx)))
              ctx))
          ctx
          (keys @entity)))

(defn- tick-system [ctx entity]
  (try
   (do-components! ctx entity/tick entity)
   (catch Throwable t
     (throw (ex-info "" (select-keys @entity [:entity/uid]) t))
     ctx)))

(extend-type api.context.Context
  api.context/EntityComponentSystem
  (all-entities [ctx]
    (vals (uids-entities ctx)))

  (get-entity [ctx uid]
    (get (uids-entities ctx) uid))

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
    (ctx/do! ctx (for [entity (filter (comp :entity/destroyed? deref) (ctx/all-entities ctx))
                       component @entity]
                   (fn [ctx]
                     (entity/destroy component entity ctx))))))

(defmethod effect/do! :tx.entity/assoc [[_ entity k v] ctx]
  (assert (keyword? k))
  (swap! entity assoc k v)
  ctx)

(defmethod effect/do! :tx.entity/assoc-in [[_ entity ks v] ctx]
  (swap! entity assoc-in ks v)
  ctx)

(defmethod effect/do! :tx.entity/dissoc [[_ entity k] ctx]
  (assert (keyword? k))
  (swap! entity dissoc k)
  ctx)

(defmethod effect/do! :tx.entity/dissoc-in [[_ entity ks] ctx]
  (assert (> (count ks) 1))
  (swap! entity update-in (drop-last ks) dissoc (last ks))
  ctx)

(defmethod effect/do! :tx.entity/update-in [[_ entity ks f] ctx]
  (swap! entity update-in ks f)
  ctx)
