(ns context.properties
  (:require [clojure.edn :as edn]
            [core.component :as component]
            [gdl.context :as ctx :refer [get-sprite create-image]]
            [gdl.graphics.animation :as animation]
            [utils.core :refer [safe-get]]))

; internally we just call it 'ids->properties' or something ... not db....

; TODO all data private -> can make record out of context later!!!
; dont access internals of data structure ... ? ?

; TODO also don't hardcode malli dependency ... don't hardcode anything
; take it to its logical conclusion



(require 'gdl.libgdx.context.image-drawer-creator)

(defn- deserialize-image [context {:keys [file sub-image-bounds]}]
  {:pre [file]}
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      ; TODO get-sprite does not return Image record => do @ image itself.
      (gdl.libgdx.context.image-drawer-creator/map->Image
       (get-sprite context
                   {:file file
                    :tilew tileh
                    :tileh tilew}
                   [(int (/ sprite-x tilew))
                    (int (/ sprite-y tileh))])))
    (create-image context file)))

(defn- serialize-image [image]
  (select-keys image [:file :sub-image-bounds]))

(defn- deserialize-animation [context {:keys [frames frame-duration looping?]}]
  (animation/create (map #(deserialize-image context %) frames)
                    :frame-duration frame-duration
                    :looping? looping?))

(defn- serialize-animation [animation]
  (-> animation
      (update :frames #(map serialize-image %))
      (select-keys [:frames :frame-duration :looping?])))

(defn- deserialize [context data]
  (->> data
       (#(if (:property/image %)
           (update % :property/image (fn [img] (deserialize-image context img)))
           %))
       ; audiovisual
       (#(if (:entity/animation %)
           (update % :entity/animation (fn [anim] (deserialize-animation context anim)))
           %))
       (#(if (:entity/animation (:property/entity %))
           (update-in % [:property/entity :entity/animation] (fn [anim] (deserialize-animation context anim)))
           %))))

; Other approaches to serialization:
; * multimethod & postwalk like cdq & use records ... or metadata hmmm , but then have these records there with nil fields etc.
; * print-dup prints weird stuff like #Float 0.5
; * print-method fucks up console printing, would have to add methods and remove methods during save/load
; => simplest way: just define keys which are assets (which are all the same anyway at the moment)
(defn- serialize [data]
  (->> data
       (#(if (:property/image %) (update % :property/image serialize-image) %))
       ; audiovisual
       (#(if (:entity/animation %)
           (update % :entity/animation serialize-animation) %))
       (#(if (:entity/animation (:property/entity %))
           (update-in % [:property/entity :entity/animation] serialize-animation) %))))



(defn- load-edn [context file]
  (let [properties (-> file slurp edn/read-string)] ; TODO use .internal Gdx/files  => part of context protocol
    (assert (apply distinct? (map :property/id properties)))
    (->> properties
         (map #(cdq.api.context/validate context %))
         (map #(deserialize context %))
         (#(zipmap (map :property/id %) %)))))

(component/def :context/properties {}
  {:keys [file]}
  (ctx/create [_ ctx]
    {:file file
     :db (load-edn ctx file)}))

(defn- pprint-spit [file data]
  (binding [*print-level* nil]
    (->> data
         clojure.pprint/pprint
         with-out-str
         (spit file))))

; property -> type -> type -> sort-order .....

(defn- sort-by-type [ctx properties-values]
  (sort-by #(->> %
                 (cdq.api.context/property->type ctx)
                 (cdq.api.context/edn-file-sort-order ctx))
           properties-values))

(def ^:private write-to-file? true)

(defn- write-properties-to-file! [{{:keys [db file]} :context/properties :as ctx}]
  (when write-to-file?
    (.start
     (Thread.
      (fn []
        (->> db
             vals
             (sort-by-type ctx)
             (map serialize)
             (map #(into (sorted-map) %))
             (pprint-spit file)))))))

(comment
 ; # Add new attributes => make into fn for property-type apply fn to all props
 (let [ctx @gdl.app/current-context
       props (cdq.api.context/all-properties ctx :property.type/weapon)
       props (for [prop props]
               (-> prop
                   (assoc :skill/start-action-sound "sounds/slash.wav"
                          :skill/action-time-modifier-key :attack-speed)))]
   (def write-to-file? false)
   (doseq [prop props]
     (swap! gdl.app/current-context update :context/properties update! prop))
   (def ^:private write-to-file? true)
   (swap! gdl.app/current-context update :context/properties update! (cdq.api.context/get-property ctx :creatures/vampire))
   nil)
 )


(extend-type gdl.context.Context
  cdq.api.context/PropertyStore
  (get-property [{{:keys [db]} :context/properties} id]
    (safe-get db id))

  (all-properties [{{:keys [db]} :context/properties :as ctx} property-type]
    (filter #(cdq.api.context/of-type? ctx property-type %) (vals db)))

  (update! [{{:keys [db]} :context/properties :as ctx}
            {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id) ; <=  part of validate - but misc does not have property/id -> add !
           (contains? db id)]}
    (cdq.api.context/validate ctx property :humanize? true)
    ;(binding [*print-level* nil] (clojure.pprint/pprint property))
    (let [new-ctx (update-in ctx [:context/properites :db] assoc id property)]
      (write-properties-to-file! new-ctx)
      new-ctx))

  (delete! [{{:keys [db]} :context/properties :as ctx}
            property-id]
    {:pre [(contains? db property-id)]}
    (let [new-ctx (update-in ctx [:context/properties :db] dissoc property-id)]
      (write-properties-to-file! new-ctx)
      new-ctx)))
