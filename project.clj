(def libgdx-version "1.12.0")

(defproject core "-SNAPSHOT"
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 ; gdl
                 [nrepl "0.9.0"]
                 [org.clojure/tools.namespace "1.3.0"]
                 [org.clj-commons/pretty "2.0.1"]
                 [com.badlogicgames.gdx/gdx                       ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-platform              ~libgdx-version :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl3        ~libgdx-version]
                 ;[com.badlogicgames.gdx/gdx-lwjgl3-glfw-awt-macos ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-freetype              ~libgdx-version]
                 [com.badlogicgames.gdx/gdx-freetype-platform     ~libgdx-version :classifier "natives-desktop"]
                 [com.kotcrab.vis/vis-ui "1.5.2"]
                 [space.earlygrey/shapedrawer "2.5.0"]
                 ; cdq
                 [com.github.damn/grid2d "1.0"]
                 [reduce-fsm "0.1.4"]
                 [metosin/malli "0.13.0"]
                 [lein-hiera "2.0.0"]]
  :plugins [[jonase/eastwood "1.2.2"]
            [lein-ancient "1.0.0-RC3"]
            [lein-codox "0.10.8"]
            [lein-hiera "2.0.0"]]

  :java-source-paths ["src-java"]

  :target-path "target/%s/" ; https://stackoverflow.com/questions/44246924/clojure-tools-namespace-refresh-fails-with-no-namespace-foo
  :uberjar-name "cdq_3.jar"
  :omit-source true
  :jvm-opts ["-Xms256m"
             "-Xmx256m"
             "-Dvisualvm.display.name=CDQ"
             "-XX:-OmitStackTraceInFastThrow" ; disappeared stacktraces
             ; for visualvm profiling
             "-Dcom.sun.management.jmxremote=true"
             "-Dcom.sun.management.jmxremote.port=20000"
             "-Dcom.sun.management.jmxremote.ssl=false"
             "-Dcom.sun.management.jmxremote.authenticate=false"
             ]
  ; this from engine, what purpose?
  ;:javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]

  :global-vars {*warn-on-reflection* true
                *print-level* 3
                ;*assert* false
                ;*unchecked-math* :warn-on-boxed
                }

  :aliases {"app"        ["run" "-m" "app.dev" "app"        "-main"]
            "gdl.simple" ["run" "-m" "app.dev" "gdl.simple" "app"]})

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
  (def only-component-ns #{"api" "app" "core" "math" "utils" "data" "dev"
                           "property"
                           "properties"
                           "modifier"
                           "tx"
                           "world"
                           "screens.options-menu"})
  (hiera/graph
   {:sources #{"src"}
    :output "target/hiera"
    :layout :horizontal
    :cluster-depth 1
    :external false
    ;:ignore only-component-ns
    :ignore #{"data"
              "utils.wasd-movement" ; utils shouldnt depend on math -> this is controls ....
              "utils"
              "core"
              "api"
              "math"
              "widgets"
              "world"
              "mapgen"
              "graphics"
              "tx"
              "property"
              "modifier"
              "properties"
              "entity-state.player-item-on-cursor"
              ; entity-state.player-item-on-cursor -> entity.inventory directly ....
              "app"
              "context.ui.config" ; move out of context, just settings .... shouldnt depend entity -> context
              ; spawn & world -> entity-state also remove (move to tx/creature the 2 FSM's) .... or to 'fsms/'
              "entity-state"
              "effect"
              "dev"
              "screens.options-menu" ; remove hardcoded deps
              "screens"

              ; => rest only context/entity is problematic ....
              ; components should not depend on each other directly ....


              }

    }))
 )
