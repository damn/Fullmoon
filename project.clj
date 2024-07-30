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

  :global-vars {*warn-on-reflection* false
                *print-level* 3
                ;*assert* false
                ;*unchecked-math* :warn-on-boxed
                }

  :aliases {"app"        ["run" "-m" "gdl.libgdx.dev" "app"        "-main"]
            "gdl.simple" ["run" "-m" "gdl.libgdx.dev" "gdl.simple" "app"]})

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
    ;:cluster-depth 3
    :external false
    :ignore #{"api"
              "gdl"
              "core.component"
              "utils.core"
              "data.val-max"
              "cdq.attributes"
              "screens.options-menu"
              }}))

 ; most complex ns:
 "screens.options-menu"
 "screens.main-menu"
 "app"
 "screens.game"
 "cdq.tx.all"
 "context.world"
 "cdq.tx.spawn"
 "context.ui.actors"
 "cdq.entity.all"
 "cdq.state.player"
 "gdl.libgdx.app"
 "cdq.state.npc"

 ; TODO I can also just see which namespaces have the most dependencies ... (on non-API namespaces !!!)

 ; => only focus on this - to de-tangle everything, pull things out , only depend on API's
 ; e.g. screens.map-editor -> screens.property-editor
 ; or in game screen the hardcoded hotkeys and not working in debug mode only ...
 ; =>  Uebersicht !! Am wichtigste, => don't get lost in detail
 ;; => make clear what is 'detail' and what not ...
 ;; -> otherwise _impossible_ !
 ; even keep inconsistent names (but the 'api' thing is interesting, but can even keep gdl. something
 ; => I see anyway in one glance what's hardcoded and what not .... work fast! names can change later even ... dont move around stuff so much just
 ; _PULL OUT_


 ; => also pull out things 1 by 1 minimal changes to restore worst offenders ...etc.
 ; => e.g. 1 change in map editor -> now property editor screen the worst offender...


 ; cdq.context.game => screens.main-menu
 ; cdq.screens.options-menu => debug-menu extra ....

 ;;

 ; cdq.tx => cdq.effect

 ; cdq.tx.spawn -> cdq.state.npc
 ; wasd-movement

 ; TODO fix cdq.entity namespace required manually (movement, body, inventory)
 ; => namespace package flow only down ... ?!
 ; inventory
 ; body



 )
