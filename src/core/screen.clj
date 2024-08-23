(ns core.screen)

(defprotocol Screen
  (show   [_ context])
  (hide   [_ context])
  (render! [_ app-state])
  (render [_ context]))
