(in-ns 'clojure.ctx)

(def ^:private add-metadoc? true)

(defn- add-metadoc! []
  (doseq [[doc-cat syms] (edn/read-string (slurp "doc_categories.edn"))
          sym syms]
    (try (alter-meta! (resolve sym) assoc :metadoc/categories #{doc-cat})
         (catch Throwable t
           (throw (ex-info "" {:sym sym} t)))))

  (doseq [[sym avar] (ns-publics *ns*)
          :when (::system? (meta avar))]
    (alter-meta! (resolve sym) assoc :metadoc/categories #{:component-systems})))

(defn- anony-class? [[sym avar]]
  (instance? java.lang.Class @avar))

(defn- record-constructor? [[sym avar]]
  (re-find #"(map)?->\p{Upper}" (name sym)))

; TODO only funcs, no macros
; what about record constructors, refer-all -> need to make either private or
; also highlight them ....
; only for categorization not necessary
(defn- vimstuff []
  (spit "vimstuff"
        (apply str
               (remove #{"defcomponent" "defsystem"}
                       (interpose " , " (map str (keys (->> (ns-publics *ns*)
                                                            (remove anony-class?)))))))))

(defn- relevant-ns-publics []
  (->> (ns-publics *ns*)
       (remove anony-class?)
       (remove record-constructor?)))
; 1. macros separate
; 2. defsystems separate
; 3. 'v-'
; 4. protocols ?1 protocol functions included ?!

#_(spit "testo"
        (str/join "\n"
                  (for [[asym avar] (sort-by first (relevant-ns-publics))]
                    (str asym " " (:arglists (meta avar)))
                    )
                  )
        )

(comment
 (spit "relevant_ns_publics"
       (str/join "\n" (sort (map first (relevant-ns-publics))))))
; = 264 public vars
; next remove ->Foo and map->Foo

#_(let [[asym avar] (first (relevant-ns-publics))]
    (str asym " "(:arglists (meta avar)))
    )

#_(when add-metadoc?
  (add-metadoc!))
