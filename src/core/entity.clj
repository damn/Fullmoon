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
