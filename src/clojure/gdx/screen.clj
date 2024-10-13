(ns clojure.gdx.screen
  (:require [core.component :refer [defsystem] :as component]
            [utils.core :refer [bind-root]]))

(declare ^:private screen-k
         ^:private screens)

(defn current []
  [screen-k (screen-k screens)])

(defsystem enter)
(defmethod enter :default [_])

(defsystem exit)
(defmethod exit :default  [_])

(defsystem render!)

(defsystem render)
(defmethod render :default [_])

(defn change
  "Calls `exit` on the current-screen (if there is one).
  Calls `enter` on the new screen."
  [new-k]
  (when-let [v (and (bound? #'screen-k) (screen-k screens))]
    (exit [screen-k v]))
  (let [v (new-k screens)]
    (assert v (str "Cannot find screen with key: " new-k))
    (bind-root #'screen-k new-k)
    (enter [new-k v])))

(defn create-all! [screen-ks]
  (bind-root #'screens (component/create-vs (zipmap screen-ks (repeat nil))))
  (change (ffirst screens)))

(defn dispose-all! []
  ; https://github.com/damn/core/issues/41
  )

