(ns dev.javafx
  (:import (javafx.application Application Platform)))

(defn init []
  (Platform/setImplicitExit false)
  ; otherwise cannot find class in dev mode w. ns-refresh, so create symbol
  (let [class (eval (ns-name *ns*))]
    (.start (Thread. #(Application/launch class (into-array String [""]))))))

(defmacro run
  "With this macro what you run is run in the JavaFX Application thread.
  Is needed for all calls related with JavaFx"
  [& code]
  `(javafx.application.Platform/runLater (fn [] ~@code)))
