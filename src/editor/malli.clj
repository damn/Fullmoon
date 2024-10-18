(ns editor.malli
  (:require [clojure.set :as set]))

(defn map-keys [m-schema]
  (let [[_m _p & ks] m-schema]
    (for [[k m? _schema] ks]
      k)))

(defn- map-form-k->properties
  "Given a map schema gives a map of key to key properties (like :optional)."
  [m-schema]
  (let [[_m _p & ks] m-schema]
    (into {} (for [[k m? _schema] ks]
               [k (if (map? m?) m?)]))))

(defn optional? [k map-schema]
  (:optional (k (map-form-k->properties map-schema))))

(defn- optional-keyset [m-schema]
  (set (filter #(optional? % m-schema) (map-keys m-schema))))

(comment
 (= (optional-keyset
     [:map {:closed true}
      [:foo]
      [:bar]
      [:baz {:optional true}]
      [:boz {:optional false}]
      [:asdf {:optional true}]])
    [:baz :asdf])

 )

(defn optional-keys-left [m-schema m]
  (seq (set/difference (optional-keyset m-schema)
                       (set (keys m)))))
