(ns utils.reflect
  (:require [clojure.string :as str]
            [clojure.reflect :refer [type-reflect]]))

(defn- relevant-fields [class-str field-type]
  (->> class-str
       symbol
       eval
       type-reflect
       :members
       (filter #(= field-type (:type %)))))

(defn- ->clojure-symbol [field]
  (-> field :name name str/lower-case (str/replace #"_" "-") symbol))

; smells a little, but its necessary to use
; * host platform colors without having to know its libgdx/java (for exapmle clojurescript, cljc files, any other plattform ...)
; * with no performance penalties for converting e.g. keywords to color instances
(defn bind-roots [class-str field-type target-ns]
  (doseq [field (relevant-fields class-str field-type)
          :let [avar (find-var (symbol target-ns (str (->clojure-symbol field))))
                value (eval (symbol class-str (str (:name field))))]]
    ;(println "Writing "  avar " to " value)
    (.bindRoot ^clojure.lang.Var avar value)))

(comment

 (defn- write-declarations [& {:keys [class-str field-type file]}]
   (->> (relevant-fields class-str field-type)
        (map ->clojure-symbol)
        sort
        clojure.pprint/pprint
        with-out-str
        (spit file)))

 (write-declarations :class-str "com.badlogic.gdx.graphics.Color"
                     :field-type 'com.badlogic.gdx.graphics.Color
                     :file "src/api.graphics.color.clj")

 (write-declarations :class-str "com.badlogic.gdx.Input$Keys"
                     :field-type 'int
                     :file "src/api.input/keys.clj")

 (write-declarations :class-str "com.badlogic.gdx.Input$Buttons"
                     :field-type 'int
                     :file "src/api.input/buttons.clj"))
