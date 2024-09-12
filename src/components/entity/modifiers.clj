(ns components.entity.modifiers
  (:require [utils.core :as utils]
            [core.component :as component :refer [defcomponent]]
            core.entity
            [core.modifiers :as modifiers]))

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:tx/update-in entity [:entity/modifiers modifier-k operation-k] (f value)]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (utils/remove-one values value)))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [_value] :fn))
    [[:tx/update-in :entity [:entity/modifiers :modifier/hp :op/max-inc] :fn]
     [:tx/update-in :entity [:entity/modifiers :modifier/hp :op/max-mult] :fn]
     [:tx/update-in :entity [:entity/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

(defcomponent :tx/apply-modifiers
  (component/do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers conj-value)))

(defcomponent :tx/reverse-modifiers
  (component/do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers remove-value)))

; DRY ->effective-value (summing)
; also: sort-by op/order @ modifier/info-text itself (so player will see applied order)
(defn- sum-operation-values [modifiers]
  (for [[modifier-k operations] modifiers
        :let [operations (for [[operation-k values] operations
                               :let [value (apply + values)]
                               :when (not (zero? value))]
                           [operation-k value])]
        :when (seq operations)]
    [modifier-k operations]))

(defcomponent :entity/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/create [_ _ctx]
    (into {} (for [[modifier-k operations] modifiers]
               [modifier-k (into {} (for [[operation-k value] operations]
                                      [operation-k [value]]))])))

  (component/info-text [_ _ctx]
    (let [modifiers (sum-operation-values modifiers)]
      (when (seq modifiers)
        (modifiers/info-text modifiers)))))

(extend-type core.entity.Entity
  core.entity/Modifiers
  (->modified-value [{:keys [entity/modifiers]} modifier-k base-value]
    {:pre [(= "modifier" (namespace modifier-k))]}
    (->> modifiers
         modifier-k
         (sort-by component/order)
         (reduce (fn [base-value [operation-k values]]
                   (component/apply [operation-k (apply + values)] base-value))
                 base-value))))

(comment
 (require '[core.entity :refer [->modified-value]])
 (let [->entity (fn [modifiers]
                  (core.entity/map->Entity {:entity/modifiers modifiers}))]
   (and
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]
                                                           :op/val-mult [0.5]}})
                         :modifier/damage-deal
                         [5 10])
       [52 52])
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]}
                                    :stats/fooz-barz {:op/babu [1 2 3]}})
                         :modifier/damage-deal
                         [5 10])
       [35 35])
    (= (->modified-value (core.entity/map->Entity {})
                         :modifier/damage-deal
                         [5 10])
       [5 10])
    (= (->modified-value (->entity {:modifier/hp {:op/max-inc [10 1]
                                                  :op/max-mult [0.5]}})
                         :modifier/hp
                         [100 100])
       [100 166])
    (= (->modified-value (->entity {:modifier/movement-speed {:op/inc [2]
                                                              :op/mult [0.1 0.2]}})
                         :modifier/movement-speed
                         3)
       6.5)))
 )
