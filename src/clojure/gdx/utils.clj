(ns clojure.gdx.utils
  (:require [clojure.string :as str])
  (:import (com.badlogic.gdx.utils Disposable)))

(defn gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(defn bind-root [avar value]
  (alter-var-root avar (constantly value)))

(defn safe-get [m k]
  (let [result (get m k ::not-found)]
    (if (= result ::not-found)
      (throw (IllegalArgumentException. (str "Cannot find " (pr-str k))))
      result)))

(def dispose! Disposable/.dispose)

(defn index-of [k ^clojure.lang.PersistentVector v]
  (let [idx (.indexOf v k)]
    (if (= -1 idx)
      nil
      idx)))

