(ns api.entity
  (:require [core.component :refer [defsystem]]
            [utils.core :as utils]))

(defsystem create-component [_ components ctx])
(defsystem create           [_ entity* ctx])
(defsystem destroy          [_ entity* ctx])
(defsystem tick             [_ entity* ctx])

(defsystem info-text          [_ ctx])
(defmethod info-text :default [_ ctx])

(def z-orders [:z-order/on-ground
               :z-order/ground
               :z-order/flying
               :z-order/effect])

(def render-order (utils/define-order z-orders))

(defsystem render-below   [_ entity* g ctx])
(defsystem render-default [_ entity* g ctx])
(defsystem render-above   [_ entity* g ctx])
(defsystem render-info    [_ entity* g ctx])
(defsystem render-debug   [_ entity* g ctx])

(def render-systems [render-below
                     render-default
                     render-above
                     render-info])

(defrecord Entity [])

(defprotocol Body
  (position [_] "Center float coordinates.")
  (tile [_] "Center integer coordinates")
  (direction [_ other-entity*] "Returns direction vector from this entity to the other entity.")
  (z-order [_]))

(defprotocol State
  (state [_])
  (state-obj [_]))

(defprotocol Skills
  (has-skill? [_ skill]))

(defprotocol Faction
  (enemy-faction [_])
  (friendly-faction [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (stat [_ stat] "Calculating value of the stat w. modifiers"))
