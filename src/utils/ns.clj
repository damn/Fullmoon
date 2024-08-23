(ns utils.ns
  (:require [clojure.string :as str]))

(defn get-namespaces [packages]
  (filter #(packages (first (str/split (name (ns-name %)) #"\.")))
          (all-ns)))

(defn get-vars [nmspace condition]
  (for [[sym avar] (ns-interns nmspace)
        :when (condition avar)]
    avar))

(comment
 (clojure.pprint/pprint
  (enumeration-seq (.getResources (ClassLoader/getSystemClassLoader) "components")))

 (clojure.pprint/pprint
  (seq (.getDefinedPackages (ClassLoader/getSystemClassLoader))))

 )
