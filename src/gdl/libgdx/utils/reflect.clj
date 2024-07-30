(ns ^:no-doc gdl.libgdx.utils.reflect
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
                     :file "src/gdl/graphics/color.clj")

 (write-declarations :class-str "com.badlogic.gdx.Input$Keys"
                     :field-type 'int
                     :file "src/gdl/input/keys.clj")

 (write-declarations :class-str "com.badlogic.gdx.Input$Buttons"
                     :field-type 'int
                     :file "src/gdl/input/buttons.clj"))
