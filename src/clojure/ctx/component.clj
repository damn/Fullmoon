(in-ns 'clojure.ctx)

(defsystem ->mk "Create component value. Default returns v." [_ ctx])
(defmethod ->mk :default [[_ v] _ctx] v)

(defsystem ^:private destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

