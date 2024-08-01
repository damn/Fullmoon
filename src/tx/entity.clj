(ns tx.entity
  (:require [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/assoc [[_ entity k v] _ctx]
  (assert (keyword? k))
  (swap! entity assoc k v)
  nil)

(defmethod transact! :tx.entity/assoc-in [[_ entity ks v] _ctx]
  (swap! entity assoc-in ks v)
  nil)

(defmethod transact! :tx.entity/dissoc [[_ entity k] _ctx]
  (assert (keyword? k))
  (swap! entity dissoc k)
  nil)

(defmethod transact! :tx.entity/dissoc-in [[_ entity ks] _ctx]
  (assert (> (count ks) 1))
  (swap! entity update-in (drop-last ks) dissoc (last ks))
  nil)

