(ns utils.debug-flag)

(def flags [])

(defmacro def-debug-flag [sym initial-value]
  `(do
    (def ~(with-meta sym {:private true}) ~initial-value)
    (alter-var-root #'flags conj (var ~sym))))
