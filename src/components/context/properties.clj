(ns components.context.properties
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.animation :as animation]
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

(defn- validate [types property & {:keys [humanize?]}]
  (let [type (property->type types property)
        schema (:schema (type types))]
    (if (try (m/validate schema property)
             (catch Throwable t
               (throw (ex-info "m/validate fail" {:property property :type type} t))))
      property
      (throw (Error. (let [explained (m/explain schema property)]
                       (str (if humanize?
                              (me/humanize explained)
                              (binding [*print-level* nil]
                                (with-out-str
                                 (clojure.pprint/pprint
                                  explained)))))))))))

(def ^:private validate? true)

(defn- load-edn [context types file]
  (let [properties (-> file slurp edn/read-string)] ; TODO use .internal Gdx/files  => part of context protocol
    (assert (apply distinct? (map :property/id properties)))
    (->> properties
         (map #(if validate? (validate types %) %))
         (map #(deserialize context %))
         (#(zipmap (map :property/id %) %)))))

(defcomponent :context/properties
  {:let {:keys [file types]}}
  (component/create [_ ctx]
    (defcomponent :property/id          {:schema [:qualified-keyword {}] :optional? false})
    (defcomponent :property/pretty-name {:schema :string})
    (defcomponent :property/image       {:schema :image})
    (defcomponent :property/animation   {:schema :animation})
    (defcomponent :property/sound       {:schema :sound})
    (component/load-ks! types)
    (let [types (component/ks->create-all types {})]
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
    (when validate? (validate types property :humanize? true))
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

(defn- property->text [{{:keys [types]} :context/properties :as ctx} property]
  ((:->text ((property->type types property) types))
   ctx
   property))

; TODO property text is without effect-ctx .... handle that different .... ?
; maybe don't even need that @ editor ??? different lvl ...
; its basically ';component - join newlines & to text ... '
; generic thing for that ...

(extend-type core.context.Context
  core.context/TooltipText
  (tooltip-text [ctx property]
    (try (->> property
              (property->text ctx)
              (remove nil?)
              (str/join "\n"))
         (catch Throwable t
           (str t))))

  (player-tooltip-text [ctx property]
    (when (ctx/player-entity ctx)
      (ctx/tooltip-text
       ; player has item @ start
       ; =>
       ; context.world/transact-create-entities-from-tiledmap
       ; =>
       ; :tx/set-item-image-in-widget
       ; =>
       ; FIXME the bug .... player-entity has not been set yet inside context/game ....
       ; same problem w. actionbar or wherever player-entity is used
       ; => avoid player-entity* at initialisation
       ; assert also earlier
       ; pass player0entity itself to actionbar/inventory ....
       ; skill window is same problem ...... if we create it @ start
       ; there will be no player
       ; or we create the tooltips on demand
       ctx ;(assoc ctx :effect/source (:entity/id (ctx/player-entity* ctx))) ; TODO !!
       property))))

(defn- migrate [property-type prop-fn]
   (def validate? false)
   (let [ctx @app/state]
     (def write-to-file? false)
     (time
      (doseq [prop (map prop-fn (ctx/all-properties ctx property-type))]
        (print (:property/id prop) ", ")
        (swap! app/state ctx/update! prop)))
     (def write-to-file? true)
     (write-properties-to-file! @app/state)
     (def validate? true)
     nil))

(comment

 (migrate :properties/creature
          #(-> %
               (update-in [:creature/entity :entity/stats]
                       assoc :stats/aggro-range 6)))
 )
