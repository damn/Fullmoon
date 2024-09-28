(in-ns 'clojure.ctx)

(defn current-screen-key [{{:keys [current]} :context/screens}]
  current)

(defn current-screen [{{:keys [current screens]} :context/screens}]
  [current (get screens current)])

(defsystem screen-enter "FIXME" [_ ctx])
(defmethod screen-enter :default [_ ctx])

(defsystem screen-exit  "FIXME" [_ ctx])
(defmethod screen-exit :default  [_ ctx])

(defn change-screen
  "Calls `screen-exit` on the current-screen (if there is one).
  Throws AssertionError when the context does not have a screen with screen-key.
  Calls `screen-enter` on the new screen and
  returns the context with current-screen set to new-screen."
{:arglists '([ctx screen-key])}
  [{{:keys [current screens]} :context/screens :as context}
   new-screen-key]
  (when-let [screen-v (and current
                           (current screens))]
    (screen-exit [current screen-v] context))

  (let [screen-v (new-screen-key screens)
        _ (assert screen-v (str "Cannot find screen with key: " new-screen-key))
        new-context (assoc-in context [:context/screens :current] new-screen-key)]
    (screen-enter [new-screen-key screen-v] new-context)
    new-context))
