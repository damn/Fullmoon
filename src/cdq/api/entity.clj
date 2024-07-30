(ns cdq.api.entity
  (:require [core.component :as component]))

(component/defn create-component [_ components ctx])
(component/defn create           [_ entity* ctx])
(component/defn destroy          [_ entity* ctx])
(component/defn tick             [_ entity* ctx])

(component/defn render-below   [_ entity* g ctx])
(component/defn render-default [_ entity* g ctx])
(component/defn render-above   [_ entity* g ctx])
(component/defn render-info    [_ entity* g ctx])
(component/defn render-debug   [_ entity* g ctx])

(defrecord Entity [])

(defprotocol Position
  (tile [_] "Center integer coordinates")
  (direction [_ other-entity*] "Returns direction vector from this entity to the other entity."))

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
