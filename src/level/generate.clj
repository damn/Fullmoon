(ns level.generate
  (:require [component.core :refer [defc]]
            [component.db :as db]
            [component.property :as property]
            [data.grid2d :as g2d]
            [gdx.tiled :as t]
            [level.area-level-grid :as area-level-grid]
            [level.creatures :as creatures]
            [level.grid :refer [scale-grid printgrid cave-grid adjacent-wall-positions flood-fill]]
            [level.modules :as modules :refer [modules-scale module-width module-height]]
            [level.tiled :refer [movement-property]]
            [level.uf-caves :as uf-caves]))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (t/add-layer! tiled-map :name "creatures" :visible false)
        creature-properties (db/all :properties/creatures)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures/with-level creature-properties area-level)]
          (when (seq creatures)
            (t/set-tile! layer position (creatures/tile (rand-nth creatures)))))))))

(def modules-file "maps/modules.tmx")

(defn generate-modules
  "The generated tiled-map needs to be disposed."
  [{:keys [world/map-size
           world/max-area-level
           world/spawn-rate]}]
  (assert (<= max-area-level map-size))
  (let [{:keys [start grid]} (cave-grid :size map-size)
        ;_ (printgrid grid)
        ;_ (println " - ")
        grid (reduce #(assoc %1 %2 :transition) grid (adjacent-wall-positions grid))
        ;_ (printgrid grid)
        ;_ (println " - ")
        _ (assert (or
                   (= #{:wall :ground :transition} (set (g2d/cells grid)))
                   (= #{:ground :transition} (set (g2d/cells grid))))
                  (str "(set (g2d/cells grid)): " (set (g2d/cells grid))))
        scale modules-scale
        scaled-grid (scale-grid grid scale)
        tiled-map (modules/place (t/load-map modules-file)
                                 scaled-grid
                                 grid
                                 (filter #(= :ground     (get grid %)) (g2d/posis grid))
                                 (filter #(= :transition (get grid %)) (g2d/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill scaled-grid start-position can-spawn?)
        ;_ (println "scaled grid with filled nil: '?' \n")
        ;_ (printgrid (reduce #(assoc %1 %2 nil) scaled-grid spawn-positions))
        ;_ (println "\n")
        {:keys [steps area-level-grid]} (area-level-grid/create
                                         :grid grid
                                         :start start
                                         :max-level max-area-level
                                         :walk-on #{:ground :transition})
        ;_ (printgrid area-level-grid)
        _ (assert (or
                   (= (set (concat [max-area-level] (range max-area-level)))
                      (set (g2d/cells area-level-grid)))
                   (= (set (concat [:wall max-area-level] (range max-area-level)))
                      (set (g2d/cells area-level-grid)))))
        scaled-area-level-grid (scale-grid area-level-grid scale)
        get-free-position-in-area-level (fn [area-level]
                                          (rand-nth
                                           (filter
                                            (fn [p]
                                              (and (= area-level (get scaled-area-level-grid p))
                                                   (#{:no-cell :undefined}
                                                    (t/property-value tiled-map :creatures p :id))))
                                            spawn-positions)))]
    (place-creatures! spawn-rate tiled-map spawn-positions scaled-area-level-grid)
    {:tiled-map tiled-map
     :start-position (get-free-position-in-area-level 0)
     :area-level-grid scaled-area-level-grid}))

(defc :world/player-creature {:schema :some #_[:s/one-to-one :properties/creatures]})
(defc :world/map-size {:schema pos-int?})
(defc :world/max-area-level {:schema pos-int?}) ; TODO <= map-size !?
(defc :world/spawn-rate {:schema pos?}) ; TODO <1 !
(defc :world/tiled-map {:schema :string})
(defc :world/components {:schema [:s/map []]})
(defc :world/generator {:schema [:enum
                                 :world.generator/tiled-map
                                 :world.generator/modules
                                 :world.generator/uf-caves]})

(property/def :properties/worlds
  {:schema [:world/generator
            :world/player-creature
            [:world/tiled-map {:optional true}]
            [:world/map-size {:optional true}]
            [:world/max-area-level {:optional true}]
            [:world/spawn-rate {:optional true}]]
   :overview {:title "Worlds"
              :columns 10}})

(defmulti generate (fn [world] (:world/generator world)))

(defmethod generate :world.generator/tiled-map [world]
  {:tiled-map (t/load-map (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [world]
  (generate-modules world))

(defmethod generate :world.generator/uf-caves [world]
  (uf-caves/create world))

(defn generate-level [world-id]
  (let [prop (db/get world-id)]
    (assoc (generate prop) :world/player-creature (:world/player-creature prop))))
