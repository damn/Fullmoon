(ns context.property-types
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [core.component :as component]
            [api.context :as ctx]
            [api.properties :as properties]))

(component/def :context/property-types {}
  property-types
  (component/create [_ _ctx]
    (component/load! property-types)
    (component/update-map property-types properties/create)))

(defn- property->text [{:keys [context/property-types] :as ctx} property]
  ((:->text (get property-types (ctx/property->type ctx property)))
   ctx
   property))

; TODO property text is without effect-ctx .... handle that different .... ?
; maybe don't even need that @ editor ??? different lvl ...
; its basically ';component - join newlines & to text ... '
; generic thing for that ...

(extend-type api.context.Context
  api.context/TooltipText
  (tooltip-text [ctx property]
    (try (->> property
              (property->text ctx)
              (remove nil?)
              (str/join "\n"))
         (catch Throwable t
           (str t))))

  (player-tooltip-text [ctx property]
    (api.context/tooltip-text
     (assoc ctx :effect/source (:context/player-entity ctx))
     property)))

(defn- of-type? [property-type {:keys [property/id]}]
  (= (namespace id)
     (:id-namespace property-type)))

(extend-type api.context.Context
  api.context/PropertyTypes
  (of-type? [{:keys [context/property-types]} property type]
    (of-type? (type property-types) property))

  (property->type [{:keys [context/property-types]} property]
    (some (fn [[type property-type]]
            (when (of-type? property-type property)
              type))
          property-types))

  (validate [{:keys [context/property-types] :as ctx} property {:keys [humanize?]}]
    (let [ptype (api.context/property->type ctx property)
          schema (:schema (get property-types ptype))]
      (if (try (m/validate schema property)
               (catch Throwable t
                 (throw (ex-info "m/validate fail" {:property property :ptype ptype} t))))
        property
        (throw (Error. (let [explained (m/explain schema property)]
                         (str (if humanize?
                                (me/humanize explained)
                                (binding [*print-level* nil]
                                  (with-out-str
                                   (clojure.pprint/pprint
                                    explained)))))))))))

  (edn-file-sort-order [{:keys [context/property-types]} property-type]
    (:edn-file-sort-order (get property-types property-type)))

  (overview [{:keys [context/property-types]} property-type]
    (-> property-types
        property-type
        :overview))

  (property-types [{:keys [context/property-types]}]
    (keys property-types)))
