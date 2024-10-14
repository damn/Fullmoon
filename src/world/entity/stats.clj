(ns world.entity.stats
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.rand :refer [rand-int-between]]
            [clojure.gdx.math.vector :as v]
            [clojure.string :as str]
            [core.component :refer [defc defc*]]
            [core.effect :as effect :refer [source target target-position target-direction]]
            [core.info :as info]
            [core.operation :as op]
            [core.tx :as tx]
            [core.val-max :as val-max]
            [utils.core :refer [readable-number k->pretty-name]]
            [world.content-grid :as content-grid]
            [world.entity :as entity :refer [line-of-sight?]]
            [world.entity.faction :as faction]
            [world.entity.modifiers :refer [->modified-value]]
            [world.grid :as grid :refer [world-grid]]
            [world.mouseover-entity :refer [mouseover-entity*]]
            [world.player :refer [world-player]]
            [world.projectile :refer [projectile-size]]
            [world.raycaster :refer [path-blocked?]]
            [world.time :refer [->counter stopped? finished-ratio]]))

(defn- defmodifier [k operations]
  (defc* k {:data [:map-optional operations]}))

(defn- stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn- stat-k->effect-k [k]
  (keyword "effect.entity" (name k)))

(defn effect-k->stat-k [effect-k]
  (keyword "stats" (name effect-k)))

(defn defstat [k {:keys [modifier-ops effect-ops] :as attr-m}]
  (defc* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops))
  (when effect-ops
    (let [effect-k (stat-k->effect-k k)]
      (defc* effect-k {:data [:map-optional effect-ops]})
      (derive effect-k :base/stat-effect))))

(defn entity-stat
  "Calculating value of the stat w. modifiers"
  [entity* stat-k]
  (when-let [base-value (stat-k (:entity/stats entity*))]
    (->modified-value entity* (stat-k->modifier-k stat-k) base-value)))

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

(defc :tx.entity.stats/pay-mana-cost
  (tx/do! [[_ entity cost]]
    (let [mana-val ((entity-stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (tx/do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )


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
(defc :effect.entity/movement-speed
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
     :editor/doc doc
     :modifier-ops operations})

  (defstat :stats/attack-speed
    {:data data
     :editor/doc doc
     :modifier-ops operations}))

; TODO bounds
(defstat :stats/armor-save
  {:data :number
   :modifier-ops [:op/inc]})

(defstat :stats/armor-pierce
  {:data :number
   :modifier-ops [:op/inc]})

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
  (defn- stats-info-texts [entity*]
    (str/join "\n"
              (for [stat-k stats-order
                    :let [value (entity-stat entity* stat-k)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

; TODO mana optional? / armor-save / armor-pierce (anyway wrong here)
; cast/attack-speed optional ?
(defc :entity/stats
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
  (entity/->v [_]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))))

  (info/text [_]
    (stats-info-texts info/*info-text-entity*))

  (entity/render-info [_ entity*]
    (when-let [hp (entity-stat entity* :stats/hp)]
      (let [ratio (val-max/ratio hp)
            {:keys [position width half-width half-height entity/mouseover?]} entity*
            [x y] position]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (g/pixels->world-units 5)
                border (g/pixels->world-units borders-px)]
            (g/draw-filled-rectangle x y width height :black)
            (g/draw-filled-rectangle (+ x border)
                                     (+ y border)
                                     (- (* width ratio) (* 2 border))
                                     (- height (* 2 border))
                                     (hpbar-color ratio))))))))

; TODO negate this value also @ use
; so can make positiive modifeirs green , negative red....
(defmodifier :modifier/damage-receive [:op/max-inc :op/max-mult])
(defmodifier :modifier/damage-deal [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])

; is called :base/stat-effect so it doesn't show up in (:data [:components-ns :effect.entity]) list in editor
; for :skill/effects
(defc :base/stat-effect
  (info/text [[k operations]]
    (str/join "\n"
              (for [operation operations]
                (str (op/info-text operation) " " (k->pretty-name k)))))

  (effect/applicable? [[k _]]
    (and target
         (entity-stat @target (effect-k->stat-k k))))

  (effect/useful? [_]
    true)

  (tx/do! [[effect-k operations]]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity-stat @target stat-k)]
        [[:e/assoc-in target [:entity/stats stat-k]
          ; TODO similar to components.entity.modifiers/->modified-value
          ; but operations not sort-by op/order ??
          ; op-apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (op/apply operation value))
                  effective-value
                  operations)]]))))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (entity-stat entity* :stats/strength) 0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect []
  [:effect.entity/damage (entity*->melee-damage @source)])

(defc :effect.entity/melee-damage
  {:data :some}
  (info/text [_]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (info/text (damage-effect))))))

  (effect/applicable? [_]
    (effect/applicable? (damage-effect)))

  (tx/do! [_]
    [(damage-effect)]))

(defn- effective-armor-save [source* target*]
  (max (- (or (entity-stat target* :stats/armor-save) 0)
          (or (entity-stat source* :stats/armor-pierce) 0))
       0))

(comment
 ; broken
 (let [source* {:entity/stats {:stats/armor-pierce 0.4}}
       target* {:entity/stats {:stats/armor-save   0.5}}]
   (effective-armor-save source* target*))
 )

(defn- armor-saves? [source* target*]
  (< (rand) (effective-armor-save source* target*)))

(defn- ->effective-damage [damage source*]
  (update damage :damage/min-max #(->modified-value source* :modifier/damage-deal %)))

(comment
 (let [->source (fn [mods] {:entity/modifiers mods})]
   (and
    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1 5 10]
                                                            :op/val-mult [0.2 0.3]
                                                            :op/max-mult [1]}}))
       {:damage/min-max [31 62]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1]}}))
       {:damage/min-max [6 10]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/max-mult [2]}}))
       {:damage/min-max [5 30]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source nil))
       {:damage/min-max [5 10]}))))

(defn- damage->text [{[min-dmg max-dmg] :damage/min-max}]
  (str min-dmg "-" max-dmg " damage"))

(defc :damage/min-max {:data :val-max})

(defc :effect.entity/damage
  {:let damage
   :data [:map [:damage/min-max]]}
  (info/text [_]
    (if source
      (let [modified (->effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (effect/applicable? [_]
    (and target
         (entity-stat @target :stats/hp)))

  (tx/do! [_]
    (let [source* @source
          target* @target
          hp (entity-stat target* :stats/hp)]
      (cond
       (zero? (hp 0))
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [;_ (println "Source unmodified damage:" damage)
             {:keys [damage/min-max]} (->effective-damage damage source*)
             ;_ (println "\nSource modified: min-max:" min-max)
             min-max (->modified-value target* :modifier/damage-receive min-max)
             ;_ (println "effective min-max: " min-max)
             dmg-amount (rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx/audiovisual (:position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:e/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))

(defc :entity/temp-modifier
  {:let {:keys [counter modifiers]}}
  (info/text [_]
    (str "[LIGHT_GRAY]Spiderweb - remaining: " (readable-number (world.time/finished-ratio counter)) "/1[]"))

  (entity/tick [[k _] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]
       [:tx/reverse-modifiers eid modifiers]]))

  (entity/render-above [_ entity*]
    (g/draw-filled-circle (:position entity*) 0.5 [0.5 0.5 0.5 0.4])))

(let [modifiers {:modifier/movement-speed {:op/mult -0.5}}
      duration 5]
  (defc :effect.entity/spiderweb
    {:data :some}
    (info/text [_]
      "Spiderweb slows 50% for 5 seconds."
      ; modifiers same like item/modifiers has info-text
      ; counter ?
      )

    (effect/applicable? [_]
      ; ?
      true)

    ; TODO stacking? (if already has k ?) or reset counter ? (see string-effect too)
    (tx/do! [_]
      (when-not (:entity/temp-modifier @target)
        [[:tx/apply-modifiers target modifiers]
         [:e/assoc target :entity/temp-modifier {:modifiers modifiers
                                                 :counter (->counter duration)}]]))))
(defc :effect.entity/convert
  {:data :some}
  (info/text [_]
    "Converts target to your side.")

  (effect/applicable? [_]
    (and target
         (= (:entity/faction @target)
            (faction/enemy @source))))

  (tx/do! [_]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:e/assoc target :entity/faction (faction/friend @source)]]))

; TODO https://github.com/damn/core/issues/29
; spawning on player both without error ?! => not valid position checked
; also what if someone moves on the target posi ? find nearby valid cell ?
; BLOCKING PLAYER MOVEMENT ! (summons no-clip with player ?)
; check not blocked position // line of sight. (part of target-position make)
; limit max. spawns
; animation/sound
; proper icon (grayscaled ?)
; keep in player movement range priority ( follow player if too far, otherwise going for enemies)
; => so they follow you around
; not try-spawn, but check valid-params & then spawn !
; new UI -> show creature body & then place
; >> but what if it is blocked the area during action-time ?? <<
(defc :effect/spawn
  {:data [:one-to-one :properties/creatures]
   :let {:keys [property/id]}}
  (effect/applicable? [_]
    (and (:entity/faction @source)
         target-position))

  (tx/do! [_]
    [[:tx/sound "sounds/bfxr_shield_consume.wav"]
     [:tx/creature {:position target-position
                    :creature-id id ; already properties/get called through one-to-one, now called again.
                    :components {:entity/state [:state/npc :npc-idle]
                                 :entity/faction (:entity/faction @source)}}]]))

(defc :effect.entity/stun
  {:data :pos
   :let duration}
  (info/text [_]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/applicable? [_]
    (and target
         (:entity/state @target)))

  (tx/do! [_]
    [[:tx/event target :stun duration]]))

(defc :effect.entity/kill
  {:data :some}
  (info/text [_]
    "Kills target")

  (effect/applicable? [_]
    (and target
         (:entity/state @target)))

  (tx/do! [_]
    [[:tx/event target :kill]]))

(defn- projectile-start-point [entity* direction size]
  (v/add (:position entity*)
         (v/scale direction
                  (+ (:radius entity*) size 0.1))))

; TODO effect/text ... shouldn't have source/target dmg stuff ....
; as it is just sent .....
; or we adjust the effect when we send it ....

(defc :effect/projectile
  {:data [:one-to-one :properties/projectiles]
   :let {:keys [entity-effects projectile/max-range] :as projectile}}
  ; TODO for npcs need target -- anyway only with direction
  (effect/applicable? [_]
    target-direction) ; faction @ source also ?

  ; TODO valid params direction has to be  non-nil (entities not los player ) ?
  (effect/useful? [_]
    (let [source-p (:position @source)
          target-p (:position @target)]
      (and (not (path-blocked? ; TODO test
                               source-p
                               target-p
                               (projectile-size projectile)))
           ; TODO not taking into account body sizes
           (< (v/distance source-p ; entity/distance function protocol EntityPosition
                          target-p)
              max-range))))

  (tx/do! [_]
    [[:tx/sound "sounds/bfxr_waypointunlock.wav"]
     [:tx/projectile
      {:position (projectile-start-point @source target-direction (projectile-size projectile))
       :direction target-direction
       :faction (:entity/faction @source)}
      projectile]]))

(comment
 ; mass shooting
 (for [direction (map math.vector/normalise
                      [[1 0]
                       [1 1]
                       [1 -1]
                       [0 1]
                       [0 -1]
                       [-1 -1]
                       [-1 1]
                       [-1 0]])]
   [:tx/projectile projectile-id ...]
   )
 )

(defc :entity-effects {:data [:components-ns :effect.entity]})

; TODO applicable targets? e.g. projectiles/effect s/???item entiteis ??? check
; same code as in render entities on world view screens/world
(defn- creatures-in-los-of-player []
  (->> (content-grid/active-entities)
       (filter #(:creature/species @%))
       (filter #(line-of-sight? @world-player @%))
       (remove #(:entity/player? @%))))

; TODO targets projectiles with -50% hp !!

(comment
 ; TODO showing one a bit further up
 ; maybe world view port is cut
 ; not quite showing correctly.
 (let [targets (creatures-in-los-of-player)]
   (count targets)
   #_(sort-by #(% 1) (map #(vector (:entity.creature/name @%)
                                   (:position @%)) targets)))

 )

(defc :effect/target-all
  {:data [:map [:entity-effects]]
   :let {:keys [entity-effects]}}
  (info/text [_]
    "[LIGHT_GRAY]All visible targets[]")

  (effect/applicable? [_]
    true)

  (effect/useful? [_]
    ; TODO
    false
    )

  (tx/do! [_]
    (let [source* @source]
      (apply concat
             (for [target (creatures-in-los-of-player)]
               [[:tx/line-render {:start (:position source*) #_(start-point source* target*)
                                  :end (:position @target)
                                  :duration 0.05
                                  :color [1 0 0 0.75]
                                  :thick? true}]
                ; some sound .... or repeat smae sound???
                ; skill do sound  / skill start sound >?
                ; problem : nested tx/effect , we are still having direction/target-position
                ; at sub-effects
                ; and no more safe - merge
                ; find a way to pass ctx / effect-ctx separate ?
                [:tx/effect {:effect/source source :effect/target target} entity-effects]]))))

  (effect/render! [_]
    (let [source* @source]
      (doseq [target* (map deref (creatures-in-los-of-player))]
        (g/draw-line (:position source*) #_(start-point source* target*)
                     (:position target*)
                     [1 0 0 0.5])))))

(defn- in-range? [entity* target* maxrange] ; == circle-collides?
  (< (- (float (v/distance (:position entity*)
                           (:position target*)))
        (float (:radius entity*))
        (float (:radius target*)))
     (float maxrange)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity* target*]
  (v/add (:position entity*)
         (v/scale (entity/direction entity* target*)
                  (:radius entity*))))

(defn- end-point [entity* target* maxrange]
  (v/add (start-point entity* target*)
         (v/scale (entity/direction entity* target*)
                  maxrange)))

(defc :maxrange {:data :pos}
  (info/text [[_ maxrange]]
    (str "[LIGHT_GRAY]Range " maxrange " meters[]")))

(defc :effect/target-entity
  {:let {:keys [maxrange entity-effects]}
   :data [:map [:entity-effects :maxrange]]
   :editor/doc "Applies entity-effects to a target if they are inside max-range & in line of sight.
               Cancels if line of sight is lost. Draws a red/yellow line wheter the target is inside the max range. If the effect is to be done and target out of range -> draws a hit-ground-effect on the max location."}

  (effect/applicable? [_]
    (and target
         (effect/effect-applicable? entity-effects)))

  (effect/useful? [_]
    (assert source)
    (assert target)
    (in-range? @source @target maxrange))

  (tx/do! [_]
    (let [source* @source
          target* @target]
      (if (in-range? source* target* maxrange)
        (cons
         [:tx/line-render {:start (start-point source* target*)
                           :end (:position target*)
                           :duration 0.05
                           :color [1 0 0 0.75]
                           :thick? true}]
         ; TODO => make new context with end-point ... and check on point entity
         ; friendly fire ?!
         ; player maybe just direction possible ?!

         ; TODO FIXME
         ; have to use tx/effect now ?!
         ; still same context ...
         ; filter applicable ?! - omg
         entity-effects

         )
        [; TODO
         ; * clicking on far away monster
         ; * hitting ground in front of you ( there is another monster )
         ; * -> it doesn't get hit ! hmmm
         ; * either use 'MISS' or get enemy entities at end-point
         [:tx/audiovisual (end-point source* target* maxrange) :audiovisuals/hit-ground]])))

  (effect/render! [_]
    (when target
      (let [source* @source
            target* @target]
        (g/draw-line (start-point source* target*)
                     (end-point source* target* maxrange)
                     (if (in-range? source* target* maxrange)
                       [1 0 0 0.5]
                       [1 1 0 0.5]))))))
