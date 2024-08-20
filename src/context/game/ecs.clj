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
  (entity/create [_ {:keys [entity/id]} _ctx]
    [[:tx/assoc-uids->entities id]])

  (entity/destroy [[_ uid] _entity _ctx]
    ;(println "Destroying " uid)
    [[:tx/dissoc-uids->entities uid]]))

(defmethod effect/do! :tx/assoc-uids->entities [[_ entity] ctx]
  {:pre [(number? (:entity/uid @entity))]}
  (update ctx :context.game/uids-entities assoc (:entity/uid @entity) entity))

(defmethod effect/do! :tx/dissoc-uids->entities [[_ uid] ctx]
  {:pre [(contains? (uids-entities ctx) uid)]}
  (update ctx :context.game/uids-entities dissoc uid))

(defmethod effect/do! ::setup-entity [[_ entity uid components] ctx]
  {:pre [(not (contains? components :entity/id))
         (not (contains? components :entity/uid))]}
  (reset! entity (-> components
                     (assoc :entity/id entity :entity/uid uid)
                     (component/update-map entity/create-component components ctx)
                     map->Entity))
  ctx)

(defmethod effect/do! ::create-components [[_ entity] ctx]
  (apply concat (component/apply-system entity/create @entity ctx)))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defmethod effect/do! :tx/create [[_ components] ctx]
  (let [entity (atom nil)]
    [[::setup-entity entity (unique-number!) components]
     [::create-components entity]]))

(defmethod effect/do! :tx/destroy [[_ entity] ctx]
  ;(println "Mark as destroy:" (:entity/uid @entity) " " (keys @entity))
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

; Assumes system does not affect entity* ! !
; order not important ??? idk

; TODO also entity* is not updated inbetween ....
; the component needs a reference ? atom ?
; => components itself need to be entities ??? if they are maps ???
; -> we need to pass entity not entity*

; we can deref it before every do! call
; also in tx/effect deref source/target then
(defn- do-components! [ctx system entity]
  (reduce (fn [ctx component-k]
            (if-let [component-v (component-k @entity)] ; if a component gets removed/dissoc'd during another system call of the same entity
              (let [component [component-k component-v]]
               (ctx/do! ctx (system component entity ctx)))
              ctx))
          ctx
          (keys @entity)))

; how do actual entity-component systems work
; what if a component gets destroyed? do I check each one for :destroyed? before calling a system ?
; is order even important (parallelization)
; do all components have to be maps ?
; in my case not ?

; 1. do we really need to pass entity to system component
; 2. can components themself be entities
; and each system knows which components are there and just runs through them ?
; (like datomic every 'component' and id)
; 3. do we need to deref?
; only 2 shout we access other stuff
; other components could merge together (delete-after-animation) ??
; state ??

; 3.1 if we do not deref it
; we are maybe updating components who are no longer exist
; because the outer @entity
; which might add the components back again
; so the (k @entity) was useful ! dont just remove it now
; but exlain ..

; if we just manage components and not entities
; then we need a function get-entity
; which creates this associative view like in datomic

; then we don't need default values for systems

; then we don;t pass entity but eid later?

; Its anyway not :entity/* but :component/* ....
; makes it more clear ....

; do entity/create also then ... and all state exit/enter/..

; and screens/states/gui-widgets/ as components

; see entity/tick of body
; all this assoc-in ....
; we need to make components into entities

; there are no 'entity' anymore only 'eid' and entity is then get-entity ctx eid

(defn- tick-system [ctx entity]
  (try
   (do-components! ctx entity/tick entity)
   (catch Throwable t
     (throw (ex-info "" (select-keys @entity [:entity/uid]) t))
     ctx)))

; apply-system reduces instead
; with the system fns

(defn- destroy-system [ctx entity]
  (do-components! ctx entity/destroy entity))

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
    (->> ctx
         ctx/all-entities
         (filter (comp :entity/destroyed? deref))
         (reduce destroy-system ctx))))

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
