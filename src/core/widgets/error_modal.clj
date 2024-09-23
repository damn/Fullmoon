(ns core.widgets.error-modal
  (:require [clj-commons.pretty.repl :as p]
            [core.gdx.scene2d.ui :as ui]
            [core.screens.stage :as stage]))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *out* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  {:added "1.0"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn error-window! [ctx throwable]
  (binding [*print-level* 5]
    (p/pretty-pst throwable 24))
  (stage/add-actor! ctx
                    (ui/->window {:title "Error"
                                  :rows [[(ui/->label (binding [*print-level* 3]
                                                        (with-err-str
                                                          (clojure.repl/pst throwable))))]]
                                  :modal? true
                                  :close-button? true
                                  :close-on-escape? true
                                  :center? true
                                  :pack? true})))
