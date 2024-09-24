(ns core.app)

(def ^{:doc "An atom referencing the current context. Only use by ui-callbacks or for development/debugging."}
  state (atom nil))
