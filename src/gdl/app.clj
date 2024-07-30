(ns gdl.app
  (:require [gdl.context :refer [change-screen]]))

(def current-context (atom nil))

(defn change-screen!
  "change-screen is dangerous, because it swap!s the current-context atom
  and then the screen render might continue with another outdated context.
  So do it always at end of a frame."
  [new-screen-key]
  (swap! current-context change-screen new-screen-key))
