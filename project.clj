(defproject clojure.ctx "-SNAPSHOT"

  :repositories [["jitpack" "https://jitpack.io"]]

  ; this works for codox ...
  ; if reduce-fsm / nrepl / pretty / tools.namespace in clojure.gdx it doesnt work
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.github.damn/clojure.gdx "-SNAPSHOT"]
                 [metosin/malli "0.13.0"]
                 [metadoc "0.2.9"]
                 [lein-hiera "2.0.0"]
                 [lein-codox "0.10.8"]
                 [reduce-fsm "0.1.4"]

                 [nrepl "0.9.0"]
                 [org.clj-commons/pretty "2.0.1"]
                 [org.clojure/tools.namespace "1.3.0"]
                 ]

  :plugins [[lein-hiera "2.0.0"]
            [lein-codox "0.10.8"]]

  :java-source-paths ["java-src"]
  :target-path "target/%s/" ; https://stackoverflow.com/questions/44246924/clojure-tools-namespace-refresh-fails-with-no-namespace-foo
  :uberjar-name "cdq_3.jar"
  :omit-source true
  :jvm-opts ["-Xms256m"
             "-Xmx256m"
             "-Dvisualvm.display.name=CDQ"
             "-XX:-OmitStackTraceInFastThrow" ; disappeared stacktraces
             ; for visualvm profiling
             ;"-Dcom.sun.management.jmxremote=true"
             ;"-Dcom.sun.management.jmxremote.port=20000"
             ;"-Dcom.sun.management.jmxremote.ssl=false"
             ;"-Dcom.sun.management.jmxremote.authenticate=false"
             ]
  :codox {:source-uri "https://github.com/damn/clojure.ctx/blob/main/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          ;:exclude-vars #"(map)?->\p{Upper}" ; TODO not working
          :writer metadoc.writers.codox/write-docs}

  ; this from engine, what purpose?
  ;:javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :global-vars {*warn-on-reflection* true
                ;*unchecked-math* :warn-on-boxed
                ;*assert* false
                *print-level* 3
                }
  :aliases {"dev" ["run" "-m" "clojure.gdx.dev" "core.app"]}
  :main core.app)

; * Notes

; * openjdk@8 stops working with long error
; * fireplace 'cp' evaluation does not work with openJDK17
; * using openjdk@11 right now and it works.
; -> report to vim fireplace?

; :FireplaceConnect 7888

(comment
 ; https://github.com/greglook/clj-hiera/blob/main/src/hiera/main.clj
 ; 1. activate dependency first: [lein-hiera "2.0.0"]
 ; 2. export JVM_OPTS=
 ; 3. lein repl
 ; 4. eval this:
 (do
  (require '[hiera.main :as hiera])
  (hiera/graph
   {:sources #{"src"}
    :output "target/hiera"
    :layout :horizontal
    :external false
    :ignore #{"dev" ; ignore
              "app"

              "gdx"   ; 1. layer
              "math"  ; 2. layer
              "utils" ; 3. layer
              "core"  ; 4. layer

              ; is ok
              "components.entity"
              "components.properties"
              }}))

 ; Current problems: simple!
 ; lein hiera :layout :horizontal :ignore "#{gdx, utils, math, mapgen, core.component, components.entity-state.load, core.entity, core.context, core.graphics, core.world, core.operation, core.modifiers, core.inventory}"

 )
