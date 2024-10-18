(ns gdx.utils
  (:require [clojure.string :as str])
  (:import (com.badlogic.gdx.utils Disposable)))

(defn gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def dispose! Disposable/.dispose)
