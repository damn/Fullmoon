(ns context.modifier
  (:require [clojure.string :as str]
            api.context
            [api.modifier :as modifier]
            [api.tx :refer [transact!]]))

(extend-type api.context.Context
  api.context/Modifier
  (modifier-text [_ modifier]
    (->> (for [component modifier]
           (modifier/text component))
         (str/join "\n"))))

(defn- gen-txs [system entity modifier]
  (for [component modifier
        :let [ks (modifier/keys component)]]
    [:tx.entity/assoc-in entity ks (system component (get-in @entity ks))]))

(defmethod transact! :tx/apply-modifier [[_ entity modifier] ctx]
  (gen-txs modifier/apply entity modifier))

(defmethod transact! :tx/reverse-modifier [[_ entity modifier] ctx]
  (gen-txs modifier/reverse entity modifier))
