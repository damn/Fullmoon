(ns entity.stats
  (:require [data.val-max :refer [val-max-ratio]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.entity :as entity]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [context.ui.config :refer (hpbar-height-px)]))

; [:tx.entity/assoc-in id [:entity/stats :stats/mana] (apply-val (entity/mana entity*) #(- % cost))]
; rule: don't know about internal state of components
; nowhere call :entity/body
; nowhere :entity/stats keyword directly
; same with contexts's etc.
; then can later make records & superfast.

(defcomponent :stats/hp data/pos-int-attr)

; required @ npc state, for cost, check if nil
(defcomponent :stats/mana data/nat-int-attr)

; for adding speed multiplier modifier -> need to take max-speed into account!
(defcomponent :stats/movement-speed data/pos-attr)

(extend-type api.entity.Entity
  entity/Stats
  (hp             [entity*] (:stats/hp (:entity/stats entity*)))
  (mana           [entity*] (:stats/mana         (:entity/stats entity*)))
  (movement-speed [entity*] (:stats/movement-speed (:entity/stats entity*))))

(defcomponent :stats/strength data/nat-int-attr)

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)]
  (defcomponent :stats/cast-speed   skill-speed-stat)
  (defcomponent :stats/attack-speed skill-speed-stat))

(defcomponent :stats/armor-save   {:widget :text-field :schema number?})
(defcomponent :stats/armor-pierce {:widget :text-field :schema number?})

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

(defcomponent :entity/stats
  (data/components-attribute :stats)
  #_(assoc (data/map-attribute :stats/movement-speed
                             :stats/strength
                             :stats/cast-speed
                             :stats/attack-speed
                             :stats/armor-save
                             :stats/armor-pierce)
    ; TODO also DRY @ modifier.all is default value 1 too...
    ; TODO default value missing... empty when created
    :default-value {:stats/movement-speed 1
                    :stats/strength 1
                    :stats/cast-speed 1
                    :stats/attack-speed 1
                    :stats/armor-save 0
                    :stats/armor-pierce 0}
    )

  (entity/create-component [[_ stats] _components _ctx]
    (-> stats
        (update :stats/hp (fn [hp] [hp hp]))
        (update :stats/mana (fn [mana] [mana mana])) ))

  (entity/render-info [_
                       {{:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?] :as entity*}
                       g
                       _ctx]
    (let [ratio (val-max-ratio (entity/hp entity*))
          [x y] (entity/position entity*)]
      (when (or (< ratio 1) mouseover?)
        (let [x (- x half-width)
              y (+ y half-height)
              height (g/pixels->world-units g hpbar-height-px) ; pre-calculate it maybe somehow, but will put too much stuff in properties?
              border (g/pixels->world-units g borders-px)] ; => can actually still use global state? idk
          (g/draw-filled-rectangle g x y width height color/black)
          (g/draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio)))))))

; New problem:
; creature have all stats now
; and missing keys

; => make optional or migrate to add all stats eveverywhere ?
