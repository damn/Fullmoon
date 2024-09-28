(in-ns 'clojure.ctx)

(defsystem ->mk "Create component value. Default returns v." [_ ctx])
(defmethod ->mk :default [[_ v] _ctx] v)

(defsystem ^:private destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_ ctx])
(defmethod info-text :default [_ ctx])

(defsystem screen-enter "FIXME" [_ ctx])
(defmethod screen-enter :default [_ ctx])

(defsystem screen-exit  "FIXME" [_ ctx])
(defmethod screen-exit :default  [_ ctx])

(defsystem ^:private screen-render! "FIXME" [_])

(defsystem screen-render "FIXME" [_ ctx])
(defmethod screen-render :default [_ ctx]
  ctx)

(defsystem do!
  " 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
  when returning a 'map?'

  2. return seq of txs -> those txs will be done recursively
  2.1 also seq of fns wih [ctx] param can be passed.

  3. return nil in case of doing nothing -> will just continue with existing ctx.

  do NOT do a effect/do inside a effect/do! because then we have to return a context
  and that means that transaction will be recorded and done double with all the sub-transactions
  in the replay mode
  we only want to record actual side effects, not transactions returning other lower level transactions"
  [_ ctx])

(defsystem applicable?
  "An effect will only be done (with do!) if this function returns truthy.
Required system for every effect, no default."
  [_ ctx])

(defsystem useful?
  "Used for NPC AI.
Called only if applicable? is truthy.
For example use for healing effect is only useful if hitpoints is < max.
Default method returns true."
  [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem create "Create entity with eid for txs side-effects. Default nil."
  [_ entity ctx])
(defmethod create :default [_ entity ctx])

(defsystem destroy "FIXME" [_ entity ctx])
(defmethod destroy :default [_ entity ctx])

(defsystem tick "FIXME" [_ entity ctx])
(defmethod tick :default [_ entity ctx])

(defsystem render-below "FIXME" [_ entity* g ctx])
(defmethod render-below :default [_ entity* g ctx])

(defsystem render "FIXME" [_ entity* g ctx])
(defmethod render :default [_ entity* g ctx])

(defsystem render-above "FIXME" [_ entity* g ctx])
(defmethod render-above :default [_ entity* g ctx])

(defsystem render-info "FIXME" [_ entity* g ctx])
(defmethod render-info :default [_ entity* g ctx])

(def ^:private render-systems [render-below
                               render
                               render-above
                               render-info])

(defsystem enter "FIXME" [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  "FIXME" [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem op-value-text "FIXME" [_])
(defsystem op-apply "FIXME" [_ base-value])
(defsystem op-order "FIXME" [_])

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
