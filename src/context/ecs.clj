(ns context.ecs
  (:require [clj-commons.pretty.repl :as p]
            [utils.core :refer [sort-by-order]]
            [core.component :as component :refer [update-map apply-system]]
            [api.context :refer [transact-all! get-entity]]
            [api.graphics :as g]
            [api.entity :as entity :refer [map->Entity]]
            [api.tx :refer [transact!]]))

(defmethod transact! :tx/assoc [[_ entity k v] _ctx]
  (assert (keyword? k))
  (swap! entity assoc k v)
  nil)

(defmethod transact! :tx/assoc-in [[_ entity ks v] _ctx]
  (swap! entity assoc-in ks v)
  nil)

(defmethod transact! :tx/dissoc [[_ entity k] _ctx]
  (assert (keyword? k))
  (swap! entity dissoc k)
  nil)

(defmethod transact! :tx/dissoc-in [[_ entity ks] _ctx]
  (assert (> (count ks) 1))
  (swap! entity update-in (drop-last ks) dissoc (last ks))
  nil)

(defmethod transact! :tx/setup-entity [[_ an-atom uid components] ctx]
  {:pre [(not (contains? components :entity/id))
         (not (contains? components :entity/uid))]}
  (let [entity* (-> components
                    (update-map entity/create-component components ctx)
                    map->Entity)]
    (reset! an-atom (assoc entity* :entity/id an-atom :entity/uid uid)))
  nil)

(defmethod transact! :tx/assoc-uids->entities [[_ entity] {:keys [context/uids->entities]}]
  {:pre [(number? (:entity/uid @entity))]}
  (swap! uids->entities assoc (:entity/uid @entity) entity)
  nil)

(defmethod transact! :tx/dissoc-uids->entities [[_ uid] {:keys [context/uids->entities]}]
  {:pre [(contains? @uids->entities uid)]}
  (swap! uids->entities dissoc uid)
  nil)

(component/def :entity/uid {} uid
  (entity/create  [_ {:keys [entity/id]} _ctx] [[:tx/assoc-uids->entities   id]])
  (entity/destroy [_ _entity*            _ctx] [[:tx/dissoc-uids->entities uid]]))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- apply-system-transact-all! [ctx system entity*]
  (run! #(transact-all! ctx %) (apply-system system entity* ctx)))

(defmethod transact! :tx/create [[_ components] ctx]
  (let [entity (atom nil)]
    (transact-all! ctx [[:tx/setup-entity entity (unique-number!) components]])
    (apply-system-transact-all! ctx entity/create @entity))
  [])

(defmethod transact! :tx/destroy [[_ entity] _ctx]
  (swap! entity assoc :entity/destroyed? true)
  nil)

(defn- handle-entity-error! [{:keys [context/thrown-error]} entity* throwable]
  (p/pretty-pst (ex-info "" (select-keys entity* [:entity/uid]) throwable))
  (reset! thrown-error throwable))

(defn- render-entity* [system
                       entity*
                       g
                       {:keys [context/thrown-error] :as ctx}]
  (try
   (dorun (apply-system system entity* g ctx))
   (catch Throwable t
     (when-not @thrown-error
       (handle-entity-error! ctx entity* t))
     (let [[x y] (:entity/position entity*)]
       (g/draw-text g
                    {:text (str "Error / entity uid: " (:entity/uid entity*))
                     :x x
                     :y y
                     :up? true})))))

; TODO similar with define-order --- same fns for z-order keyword ... same name make ?
(def ^:private render-systems [entity/render-below
                               entity/render-default
                               entity/render-above
                               entity/render-info])

(extend-type api.context.Context
  api.context/EntityComponentSystem
  (all-entities [{:keys [context/uids->entities]}]
    (vals @uids->entities))

  (get-entity [{:keys [context/uids->entities]} uid]
    (get @uids->entities uid))

  (tick-entities! [{:keys [context/thrown-error] :as ctx} entities*]
    (doseq [entity* entities*]
      (try
       (apply-system-transact-all! ctx entity/tick entity*)
       (catch Throwable t
         (handle-entity-error! ctx entity* t)))))

  (render-entities! [context g entities*]
    (doseq [entities* (map second ; FIXME lazy seq
                           (sort-by-order (group-by :entity/z-order entities*)
                                          first
                                          entity/render-order))
            system render-systems
            entity* entities*]
      (render-entity* system entity* g context))
    (doseq [entity* entities*]
      (render-entity* entity/render-debug entity* g context)))

  (remove-destroyed-entities! [{:keys [context/uids->entities] :as ctx}]
    (doseq [entity (filter (comp :entity/destroyed? deref) (vals @uids->entities))]
      (apply-system-transact-all! ctx entity/destroy @entity))))
