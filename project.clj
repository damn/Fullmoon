(def libgdx-version "1.12.1")

(defproject moon "-SNAPSHOT"
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.badlogicgames.gdx/gdx                   ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-platform          ~libgdx-version :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl3    ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-freetype          ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-freetype-platform ~libgdx-version :classifier "natives-desktop"]
                 [space.earlygrey/shapedrawer "2.5.0"]
                 [com.kotcrab.vis/vis-ui "1.5.2"]
                 [com.github.damn/grid2d "1.0"]
                 [metosin/malli "0.13.0"]
                 [com.github.damn/reduce-fsm "eb1a2c1ff0"]
                 [nrepl "0.9.0"]
                 [org.clj-commons/pretty "3.2.0"]
                 [org.clojure/tools.namespace "1.3.0"]
                 [org.openjfx/javafx-controls "22.0.1"]
                 [rewrite-clj/rewrite-clj "1.1.48"]]

  :java-source-paths ["java-src"]

  :plugins [[lein-hiera "2.0.0"]
            [lein-codox "0.10.8"]]

  :target-path "target/%s/" ; https://stackoverflow.com/questions/44246924/clojure-tools-namespace-refresh-fails-with-no-namespace-foo

  :uberjar-name "cdq_3.jar"

  :omit-source true

  ; because dev.browser 512...
  :jvm-opts ["-Xms512m"
             "-Xmx512m"
             "-Dvisualvm.display.name=CDQ"
             "-XX:-OmitStackTraceInFastThrow" ; disappeared stacktraces
             ; for visualvm profiling
             ;"-Dcom.sun.management.jmxremote=true"
             ;"-Dcom.sun.management.jmxremote.port=20000"
             ;"-Dcom.sun.management.jmxremote.ssl=false"
             ;"-Dcom.sun.management.jmxremote.authenticate=false"
             ]

  :codox {:source-uri "https://github.com/damn/moon/blob/main/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  ; this from engine, what purpose?
  ;:javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]

  :global-vars {*warn-on-reflection* false
                ;*unchecked-math* :warn-on-boxed
                ;*assert* false
                *print-level* 3}

  :profiles {:javafx  {:aot [editor.javafx dev.browser]}
             :uberjar {:aot [app.start]}}

  :main app.start)

; * Notes

; * openjdk@8 stops working with long error
; * fireplace 'cp' evaluation does not work with openJDK17
; * using openjdk@11 right now and it works.
; -> report to vim fireplace?

; :FireplaceConnect 7888
