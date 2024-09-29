(ns core.entity
  (:require [clj-commons.pretty.repl :refer [pretty-pst]]
            [clojure.gdx :refer :all]
            [clojure.ctx :refer :all]
            [clojure.string :as str]
            [data.grid2d :as grid2d]
            [malli.core :as m])
  (:load "entity/base"
         "entity/image"
         "entity/animation"
         "entity/movement"
         "entity/delete_after_duration"
         "entity/destroy_audiovisual"
         "entity/line"
         "entity/projectile"
         "entity/skills" ; -> Creature?
         "entity/faction"   ; -> Creature?
         "entity/clickable"
         "entity/mouseover"
         "entity/temp_modifier"   ; -> Creature?
         "entity/alert"   ; -> Creature?
         "entity/string_effect"   ; -> Creature?
         "entity/modifiers"   ; -> Creature?
         "entity/inventory"   ; -> Creature?
         ))

(defn- calculate-mouseover-entity [ctx]
  (let [player-entity* (player-entity* ctx)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (point->entities ctx
                                           (world-mouse-position ctx))))]
    (->> render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? ctx player-entity* %))
         first
         :entity/id)))

(defn mouseover-entity* [ctx]
  (when-let [entity (:context/mouseover-entity ctx)]
    @entity))

(defn update-mouseover-entity [ctx]
  (let [entity (if (mouse-on-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    [(when-let [old-entity (:context/mouseover-entity ctx)]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx :context/mouseover-entity entity))]))

(defsystem enter "FIXME" [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  "FIXME" [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem player-enter "FIXME" [_])
(defmethod player-enter :default [_])

(defsystem pause-game? "FIXME" [_])
(defmethod pause-game? :default [_])

(defsystem manual-tick "FIXME" [_ ctx])
(defmethod manual-tick :default [_ ctx])

(defsystem clicked-inventory-cell "FIXME" [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defsystem clicked-skillmenu-skill "FIXME" [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])
