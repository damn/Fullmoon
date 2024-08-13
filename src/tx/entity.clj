(ns tx.entity
  (:require [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/assoc [[_ entity k v] ctx]
  (assert (keyword? k))
  (swap! entity assoc k v)
  ctx)

(defmethod transact! :tx.entity/assoc-in [[_ entity ks v] ctx]
  (swap! entity assoc-in ks v)
  ctx)

(defmethod transact! :tx.entity/dissoc [[_ entity k] ctx]
  (assert (keyword? k))
  (swap! entity dissoc k)
  ctx)

(defmethod transact! :tx.entity/dissoc-in [[_ entity ks] ctx]
  (assert (> (count ks) 1))
  (swap! entity update-in (drop-last ks) dissoc (last ks))
  ctx)

(defmethod transact! :tx.entity/update-in [[_ entity ks f] ctx]
  (swap! entity update-in ks f)
  ctx)
