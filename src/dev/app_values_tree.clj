(ns dev.app-values-tree
  (:require [core.utils.ns :as ns]))

(comment

 (print-app-values-tree)

 )

(defn- protocol? [value]
  (and (instance? clojure.lang.PersistentArrayMap value)
       (:on value)))

(defn- get-non-fn-vars [nmspace]
  (ns/get-vars nmspace
               (fn [avar]
                 (let [value @avar]
                   (not (or (fn? value)
                            (instance? clojure.lang.MultiFn value)
                            #_(:method-map value) ; breaks for stage Ilookup
                            (protocol? value)
                            (instance? java.lang.Class value) ;anonymous class (proxy)
                            ))))))

(defn- print-app-values-tree []
  (spit "app-values-tree.clj"
        (with-out-str
         (clojure.pprint/pprint
          (for [nmspace (sort-by (comp name ns-name)
                                 (ns/get-namespaces
                                  (fn [first-ns-part-str]
                                    (not (#{"clojure"
                                            "nrepl"
                                            "malli"
                                            "user"
                                            "borkdude"
                                            "clj-commons"
                                            "dorothy"
                                            "reduce-fsm"}
                                          first-ns-part-str)))))
                :let [value-vars (get-non-fn-vars nmspace)]
                :when (seq value-vars)]
            [(ns-name nmspace) (map (comp symbol name symbol) value-vars)])))))
