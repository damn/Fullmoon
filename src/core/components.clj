(ns core.components
  (:require [clojure.string :as str]
            [core.component :as component]))

(def ^:private property-order [:property/id
                               :property/image
                               :property/pretty-name
                               :property/bounds])

(def ^:private item-order [:item/slot
                           :item/modifiers])

(def ^:private skill-order [:skill/action-time-modifier-key
                            :skill/action-time
                            :skill/cooldown
                            :skill/cost
                            :skill/effects
                            :skill/start-action-sound])

(def ^:private entity-order [:entity/animation
                             :entity/flying?
                             :entity/reaction-time
                             :creature/level
                             :creature/species
                             :entity.creature/name
                             :entity.creature/species
                             :entity/faction
                             :entity/state
                             :entity/stats
                             :entity/delete-after-duration
                             :entity/projectile-collision])

(def ^:private k-order
  (vec
   (concat property-order
           item-order
           skill-order
           entity-order)))

(defn- index-of [v k]
  (let [idx (.indexOf v k)]
    (if (= -1 idx)
      nil
      idx)))

(defn sort-by-order [components]
  (sort-by (fn [[k _v]]
             (index-of k-order k))
           components))



; TODO this is recursive, e.g. projcetile/collision first piercing than effects?

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))


; (components/info-text (select-keys entity* info-text-key-order)
; => the entity needs a type or property/type
; and there is defined the :info-text-key-order
; which we fetch
; anyway properties need property/type
; and properties just entities
; so
;(defcomponent :entity.type/body {:info-text-key-order})

; TODO this whole thing can be removed ??
; at least has nothing to do with context.properties anymore...
; entity/info-text? entity = bag of components ?
; core.entity is acatually core.body ?


; also properties == entities with uid property/id ?
; get-entity not get-property ?
; property -> create entity function generic make?
; no need to image->edn then ?

(defn info-text [components ctx]
  (->> components
       sort-by-order
       (keep (fn [{v 1 :as component}]
               (str (component/info-text component ctx)
                    (when (map? v)
                      (str "\n" (info-text v ctx))))))
       (str/join "\n")
       remove-newlines))

; widgets / icons ? (see WoW )
; * HP color based on ratio like hp bar samey (take same color definitions etc.)
; * mana color same in the whole app
; * red positive/green negative
; * readable-number on ->effective-value but doesn't work on val-max ->pretty-value fn ?

; :base/stat-effect ?? why not recursively?
; also stats why not modifiers has its own ?
; effect/projectile ...


; TODO then color as of component make ....
; default (name k?)

; TODO use also @ properties => text

; TODO use at \n split everywhere (entity info widget not using this)
; grep info-text or split \ntoo
; (damage ... move to dmg, modifiers?)

; colors ... widgets ...

; TODO 8 source modifiers not showing (effects)
; TODO entity.effect no color set.... ?

#_(extend-type core.context.Context
  core.context/TooltipText
  (tooltip-text [ctx property]
    (components/info-text property ctx))


 ; player just give unique uid no need to make anu drama assoc ctx?
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
