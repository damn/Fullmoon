(ns context.properties
  (:require [clojure.edn :as edn]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [data.animation :as animation]
            [utils.core :refer [safe-get]]))

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
       (#(if (:property/animation %)
           (update % :property/animation (fn [anim] (edn->animation context anim)))
           %))
       (#(if (:entity/animation (:creature/entity %))
           (update-in % [:creature/entity :entity/animation] (fn [anim] (edn->animation context anim)))
           %))))

; Other approaches to serialization:
; * multimethod & postwalk like cdq & use records ... or metadata hmmm , but then have these records there with nil fields etc.
; * print-dup prints weird stuff like #Float 0.5
; * print-method fucks up console printing, would have to add methods and remove methods during save/load
; => simplest way: just define keys which are assets (which are all the same anyway at the moment)
(defn- serialize [data]
  (->> data
       (#(if (:property/image %) (update % :property/image image->edn) %))
       (#(if (:property/animation %)
           (update % :property/animation animation->edn) %))
       (#(if (:entity/animation (:creature/entity %))
           (update-in % [:creature/entity :entity/animation] animation->edn) %))))

(defn- load-edn [context file]
  (let [properties (-> file slurp edn/read-string)] ; TODO use .internal Gdx/files  => part of context protocol
    (assert (apply distinct? (map :property/id properties)))
    (->> properties
         (map #(api.context/validate context % {}))
         (map #(deserialize context %))
         (#(zipmap (map :property/id %) %)))))

(defcomponent :context/properties {}
  (component/create [[_ {:keys [file]}] ctx]
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
                 (api.context/property->type ctx)
                 (api.context/edn-file-sort-order ctx))
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

 ; # Change properties -> disable validate @ update!

 ; == 'db - migration' !

 (let [ctx @app.state/current-context
       props (api.context/all-properties ctx :property.type/misc)
       props (for [prop props]
               (-> prop
                   (assoc
                    :item/modifier {},
                    :item/slot :inventory.slot/bag)
                   (update :property/id (fn [k] (keyword "items" (name k))))))]
   (def write-to-file? true)
   (doseq [prop props] (swap! app.state/current-context ctx/update! prop))
   nil)
 )

(extend-type api.context.Context
  api.context/PropertyStore
  (get-property [{{:keys [db]} :context/properties} id]
    (safe-get db id))

  (all-properties [{{:keys [db]} :context/properties :as ctx} type]
    (filter #(ctx/of-type? ctx % type) (vals db)))

  (update! [{{:keys [db]} :context/properties :as ctx}
            {:keys [property/id] :as property}]
    {:pre [(contains? property :property/id) ; <=  part of validate - but misc does not have property/id -> add !
           (contains? db id)]}
    (api.context/validate ctx property {:humanize? true})
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
