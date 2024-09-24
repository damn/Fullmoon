(ns core.component
  (:require [clojure.string :as str]
            [core.utils.core :refer [index-of]]))

(def ^{:doc "For defsystem and defcomponent"}
  warn-on-override? true)

(def ^{:doc "Map of all systems as key of name-string to var."}
  defsystems {})

(defmacro defsystem
  "A system is a multimethod which dispatches on ffirst.
So for a component `[k v]` it dispatches on the component-keyword `k`."
  [sys-name docstring params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when warn-on-override?
    (when-let [avar (resolve sys-name)]
      (println "WARNING: Overwriting defsystem:" avar)))
  `(do
    (defmulti ~(vary-meta sys-name
                          assoc
                          :params (list 'quote params)
                          :doc (str "[[core.component/defsystem]] with params: `" params "` \n\n " docstring))
      (fn ~(symbol (str (name sys-name))) [& args#]
        (ffirst args#)))
    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))


(defsystem destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_ ctx])
(defmethod info-text :default [_ ctx])

(def ^:private k-order
  [:property/pretty-name
   :skill/action-time-modifier-key
   :skill/action-time
   :skill/cooldown
   :skill/cost
   :skill/effects
   :creature/species
   :creature/level
   :entity/stats
   :entity/delete-after-duration
   :projectile/piercing?
   :entity/projectile-collision
   :maxrange
   :entity-effects])

(defn- sort-by-order [components]
  (sort-by (fn [[k _]] (or (index-of k k-order) 99))
           components))

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))

(defn ->text
  "Recursively generates info-text via [[core.component/info-text]]."
  [components ctx]
  (->> components
       sort-by-order
       (keep (fn [{v 1 :as component}]
               (str (try (info-text component (assoc ctx :info-text/entity* components))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after ->mk
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->text v ctx))))))
       (str/join "\n")
       remove-newlines))

(defsystem applicable?
  "An effect will only be done (with do!) if this function returns truthy.
Required system for every effect, no default."
  [_ ctx])

(defsystem useful?
  "Used for NPC AI.
Called only if applicable? is truthy.
For example use for healing effect is only useful if hitpoints is < max.
Default method returns true."
  [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render "Renders effect during active-skill state while active till done?. Default do nothing." [_ g ctx])
(defmethod render :default [_ g ctx])

; TODO all defsystem here & defsystem private
; ^:no-doc and add extra defsystem to ns-docs itself? separate from fns then (which are less)
