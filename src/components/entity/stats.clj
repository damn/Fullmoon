(ns components.entity.stats
  (:require [clojure.string :as str]
            [malli.core :as m]
            [gdx.graphics.color :as color]
            [utils.core :refer [k->pretty-name readable-number]]
            [core.val-max :refer [val-max-ratio]]
            [core.component :as component :refer [defcomponent defcomponent*]]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.stat :refer [stat-k->modifier-k defstat]]))

; TODO needs to be there for each npc - make non-removable (added to all creatures)
; and no need at created player (npc controller component?)
(defstat :stats/aggro-range   {:data :nat-int})
(defstat :stats/reaction-time {:data :pos-int})

; TODO
; @ hp says here 'Minimum' hp instead of just 'HP'
; Sets to 0 but don't kills
; Could even set to a specific value ->
; op/set-to-ratio 0.5 ....
; sets the hp to 50%...
(defstat :stats/hp
  {:data :pos-int
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

(defstat :stats/mana
  {:data :nat-int
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

; * TODO clamp/post-process effective-values @ stat-k->effective-value
; * just don't create movement-speed increases too much?
; * dont remove strength <0 or floating point modifiers  (op/int-inc ?)
; * cast/attack speed dont decrease below 0 ??

; TODO clamp between 0 and max-speed ( same as movement-speed-schema )
(defstat :stats/movement-speed
  {:data :pos;(m/form entity/movement-speed-schema)
   :modifier-ops [:op/inc :op/mult]})

; TODO show the stat in different color red/green if it was permanently modified ?
; or an icon even on the creature
; also we want audiovisuals always ...
(defcomponent :effect.entity/movement-speed
  {:data [:map [:op/mult]]})
(derive :effect.entity/movement-speed :base/stat-effect)

; TODO clamp into ->pos-int
(defstat :stats/strength
  {:data :nat-int
   :modifier-ops [:op/inc]})

; TODO here >0
(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      data :pos
      operations [:op/inc]]
  (defstat :stats/cast-speed
    {:data data
     :doc doc
     :modifier-ops operations})

  (defstat :stats/attack-speed
    {:data data
     :doc doc
     :modifier-ops operations}))

; TODO bounds
(defstat :stats/armor-save
  {:data :number
   :modifier-ops [:op/inc]})

(defstat :stats/armor-pierce
  {:data :number
   :modifier-ops [:op/inc]})


(extend-type core.entity.Entity
  entity/Stats
  (stat [entity* stat-k]
    (when-let [base-value (stat-k (:entity/stats entity*))]
      (entity/->modified-value entity* (stat-k->modifier-k stat-k) base-value))))

; TODO remove vector shaboink -> gdx.graphics.color/->color use.
(def ^:private hpbar-colors
  {:green     [0 0.8 0]
   :darkgreen [0 0.5 0]
   :yellow    [0.5 0.5 0]
   :red       [0.5 0 0]})

(defn- hpbar-color [ratio]
  (let [ratio (float ratio)
        color (cond
                (> ratio 0.75) :green
                (> ratio 0.5)  :darkgreen
                (> ratio 0.25) :yellow
                :else          :red)]
    (color hpbar-colors)))

(def ^:private borders-px 1)

(let [stats-order [:stats/hp
                   :stats/mana
                   ;:stats/movement-speed
                   :stats/strength
                   :stats/cast-speed
                   :stats/attack-speed
                   :stats/armor-save
                   ;:stats/armor-pierce
                   ;:stats/aggro-range
                   ;:stats/reaction-time
                   ]]
  (defn- stats-info-texts [entity* stats]
    (str/join "\n"
              (for [stat-k stats-order
                    :let [value (entity/stat entity* stat-k)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

; TODO mana optional? / armor-save / armor-pierce (anyway wrong here)
; cast/attack-speed optional ?
(defcomponent :entity/stats
  {:data [:map [:stats/hp
                :stats/movement-speed
                :stats/aggro-range
                :stats/reaction-time
                [:stats/mana          {:optional true}]
                [:stats/strength      {:optional true}]
                [:stats/cast-speed    {:optional true}]
                [:stats/attack-speed  {:optional true}]
                [:stats/armor-save    {:optional true}]
                [:stats/armor-pierce  {:optional true}]]]
   :let stats}
  (component/create [_ _ctx]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))))

  (component/info-text [_ {:keys [info-text/entity*]}]
    (stats-info-texts entity* stats))

  (component/render-info [_ entity* g _ctx]
    (when-let [hp (entity/stat entity* :stats/hp)]
      (let [ratio (val-max-ratio hp)
            {:keys [position width half-width half-height entity/mouseover?]} entity*
            [x y] position]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (g/pixels->world-units g entity/hpbar-height-px)
                border (g/pixels->world-units g borders-px)]
            (g/draw-filled-rectangle g x y width height color/black)
            (g/draw-filled-rectangle g
                                     (+ x border)
                                     (+ y border)
                                     (- (* width ratio) (* 2 border))
                                     (- height (* 2 border))
                                     (hpbar-color ratio))))))))

(defcomponent :tx.entity.stats/pay-mana-cost
  (component/do! [[_ entity cost] _ctx]
    (let [mana-val ((entity/stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:tx/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (component/do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:tx/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )
