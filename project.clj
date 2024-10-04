(defproject clojure.ctx "-SNAPSHOT"
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.github.damn/clojure.gdx "main-SNAPSHOT"]
                 [org.openjfx/javafx-controls "22.0.1"]]
  :plugins [[lein-hiera "2.0.0"]
            [lein-codox "0.10.8"]]
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
  :global-vars {*warn-on-reflection* false
                ;*unchecked-math* :warn-on-boxed
                ;*assert* false
                *print-level* 3}
  :aliases {"dev" ["run" "-m" "clojure.gdx.dev" "core.app"]}
  :aot [core.tool]
  :main core.app)

; * Notes

; * openjdk@8 stops working with long error
; * fireplace 'cp' evaluation does not work with openJDK17
; * using openjdk@11 right now and it works.
; -> report to vim fireplace?

; :FireplaceConnect 7888
