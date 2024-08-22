(ns utils.ns
  (:require [clojure.string :as str]))

(defn get-namespaces [packages]
  (filter #(packages (first (str/split (name (ns-name %)) #"\.")))
          (all-ns)))

(defn get-vars [nmspace condition]
  (for [[sym avar] (ns-interns nmspace)
        :when (condition avar)]
    avar))
