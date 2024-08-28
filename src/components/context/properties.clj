(ns components.context.properties
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [utils.core :refer [safe-get mapvals]]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.animation :as animation]))

(defn- edn->image [ctx {:keys [file sub-image-bounds]}]
  {:pre [file]}
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (ctx/get-sprite ctx
                      (ctx/spritesheet ctx file tilew tileh)
                      [(int (/ sprite-x tilew))
                       (int (/ sprite-y tileh))]))
    (ctx/create-image ctx file)))

(import 'com.badlogic.gdx.graphics.g2d.TextureRegion)

(defn- is-sub-texture? [^TextureRegion texture-region]
  (let [texture (.getTexture texture-region)]
    (or (not= (.getRegionWidth  texture-region) (.getWidth  texture))
        (not= (.getRegionHeight texture-region) (.getHeight texture)))))

(defn- region-bounds [^TextureRegion texture-region]
  [(.getRegionX texture-region)
   (.getRegionY texture-region)
   (.getRegionWidth texture-region)
   (.getRegionHeight texture-region)])

(defn- texture-region->file [^TextureRegion texture-region]
  (.toString (.getTextureData (.getTexture texture-region))))

(defn- image->edn [{:keys [texture-region]}] ; not serializing color,scale.
  (merge {:file (texture-region->file texture-region)}
         (if (is-sub-texture? texture-region)
           {:sub-image-bounds (region-bounds texture-region)})))

(defn- edn->animation [context {:keys [frames frame-duration looping?]}]
  (animation/create (map #(edn->image context %) frames)
                    :frame-duration frame-duration
                    :looping? looping?))

(defn- animation->edn [animation]
  (-> animation
      (update :frames #(map image->edn %))
      (select-keys [:frames :frame-duration :looping?])))

(defn- deserialize [context data]
  (->> data
       (#(if (:property/image %)
           (update % :property/image (fn [img] (edn->image context img)))
           %))
       (#(if (:entity/animation   %) (update % :entity/animation   (fn [anim] (edn->animation context anim))) %))))

; Other approaches to serialization:
; * multimethod & postwalk like cdq & use records ... or metadata hmmm , but then have these records there with nil fields etc.
; * print-dup prints weird stuff like #Float 0.5
; * print-method fucks up console printing, would have to add methods and remove methods during save/load
; => simplest way: just define keys which are assets (which are all the same anyway at the moment)
(defn- serialize [data]
  (->> data
       (#(if (:property/image %) (update % :property/image image->edn) %))
       (#(if (:entity/animation   %) (update % :entity/animation   animation->edn) %))))

(defn- of-type?
  ([property-type {:keys [property/id]}]
   (= (namespace id)
      (:id-namespace property-type)))
  ([types property type]
   (of-type? (type types) property)))

(defn- property->type [types property]
  {:post [%]}
  (some (fn [[type property-type]]
          (when (of-type? property-type property)
            type))
        types))

(defn- validation-error-message [schema property]
  (let [explained (m/explain schema property)]
    (str (me/humanize explained))))

(def ^:private validate? true)

(defn- validate [types property]
  (if validate?
    (let [type (property->type types property)
          schema (:schema (type types))]
      (if (try (m/validate schema property)
               (catch Throwable t
                 (throw (ex-info "m/validate fail" {:property property :type type} t))))
        property
        (throw (ex-info (validation-error-message schema property)
                        {:property property}))))
    property))

(defn- load-edn [context types file]
  (let [properties (-> file slurp edn/read-string)] ; TODO use .internal Gdx/files  => part of context protocol
    (assert (apply distinct? (map :property/id properties)))
    (->> properties
         (map #(validate types %))
         (map #(deserialize context %))
         (#(zipmap (map :property/id %) %)))))

(defn- map-attribute-schema [[id-attribute attr-ks]]
  (let [schema-form (apply vector :map {:closed true} id-attribute
                           (component/attribute-schema attr-ks))]
    (try (m/schema schema-form)
         (catch Throwable t
           (throw (ex-info "" {:schema-form schema-form} t))))))

(defcomponent :context/properties
  {:let {:keys [defcomponents types file]}}
  (component/create [_ ctx]
    (doseq [[k m] defcomponents]
      (component/defcomponent* k m :warn-on-override false))
    (let [types (component/ks->create-all types {})
          types (mapvals #(update % :schema map-attribute-schema) types)]
      {:file file
       :types types
       :db (load-edn ctx types file)})))

(defn- pprint-spit [file data]
  (binding [*print-level* nil]
    (->> data
         clojure.pprint/pprint
         with-out-str
         (spit file))))

(defn- sort-by-type [types properties-values]
  (sort-by (fn [property]
             (:edn-file-sort-order ((property->type types property) types)))
           properties-values))

(def ^:private write-to-file? true)

(defn- sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (sort-map %)
                        %)
                     (vals m)))))

(defn- write-properties-to-file! [{{:keys [types db file]} :context/properties :as ctx}]
  (when write-to-file?
    (.start
     (Thread.
      (fn []
        (->> db
             vals
             (sort-by-type types)
             (map serialize)
             (map sort-map)
             (pprint-spit file)))))))

(extend-type core.context.Context
  core.context/PropertyStore
  (get-property [{{:keys [db]} :context/properties} id]
    (safe-get db id))

  (all-properties [{{:keys [db types]} :context/properties :as ctx} type]
    (filter #(of-type? types % type) (vals db)))

  (overview [{{:keys [types]} :context/properties} property-type]
    (:overview (property-type types)))

  (property-types [{{:keys [types]} :context/properties}]
    (keys types))

  (update! [{{:keys [db types]} :context/properties :as ctx}
            {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id) ; <=  part of validate - but misc does not have property/id -> add !
           (contains? db id)]}
    (validate types property)
    ;(binding [*print-level* nil] (clojure.pprint/pprint property))
    (let [new-ctx (update-in ctx [:context/properties :db] assoc id property)]
      (write-properties-to-file! new-ctx)
      new-ctx))

  (delete! [{{:keys [db]} :context/properties :as ctx}
            property-id]
    {:pre [(contains? db property-id)]}
    (let [new-ctx (update-in ctx [:context/properties :db] dissoc property-id)]
      (write-properties-to-file! new-ctx)
      new-ctx)))

(comment

 (defn- migrate [property-type prop-fn]
   (def validate? false)
   (let [ctx @app/state]
     (def write-to-file? false)
     (time
      (doseq [prop (map prop-fn (ctx/all-properties ctx property-type))]
        (println (:property/id prop) ", " (:property/pretty-name prop))
        (swap! app/state ctx/update! prop)))
     (def write-to-file? true)
     (write-properties-to-file! @app/state)
     (def validate? true)
     nil))

 (migrate :properties/skill
          (fn [prop]
            (-> prop
                (assoc :property/pretty-name (str/capitalize (name (:property/id prop)))))))
 )
