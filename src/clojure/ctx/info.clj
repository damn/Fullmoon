(in-ns 'clojure.ctx)

(declare info-text-k-order)

(defn- sort-k-order [components]
  (sort-by (fn [[k _]] (or (index-of k info-text-k-order) 99))
           components))

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))

(defn ->info-text
  "Recursively generates info-text via [[info-text]]."
  [components ctx]
  (->> components
       sort-k-order
       (keep (fn [{v 1 :as component}]
               (str (try (info-text component (assoc ctx :info-text/entity* components))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after ->mk
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->info-text v ctx))))))
       (str/join "\n")
       remove-newlines))
