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

(defsystem create "Create component value. Default returns v." [_ ctx])
(defmethod create :default [[_ v] _ctx] v)

(defsystem destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_ ctx])
(defmethod info-text :default [_ ctx])

(defn create-vs
  "Creates a map for every component with map entries `[k (core.component/create [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (create [k v] ctx)))
          {}
          components))

(defn create-into
  "For every component `[k v]`  `(core.component/create [k v] ctx)` is non-nil
  or false, assoc's at ctx k v"
  [ctx components]
  (assert (map? ctx))
  (reduce (fn [ctx [k v]]
            (if-let [v (create [k v] ctx)]
              (assoc ctx k v)
              ctx))
          ctx
          components))

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
                           ; have a different data schema than after component/create
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->text v ctx))))))
       (str/join "\n")
       remove-newlines))


; 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
; when returning a 'map?'

; 2. return seq of txs -> those txs will be done recursively
; 2.1 also seq of fns wih [ctx] param can be passed.

; 3. return nil in case of doing nothing -> will just continue with existing ctx.

; do NOT do a effect/do inside a effect/do! because then we have to return a context
; and that means that transaction will be recorded and done double with all the sub-transactions
; in the replay mode
; we only want to record actual side effects, not transactions returning other lower level transactions
(defsystem do! "FIXME" [_ ctx])

(defsystem applicable?
  "An effect will only be done (with component/do!) if this function returns truthy.
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
