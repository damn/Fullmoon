(ns gdx.app
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.ApplicationListener))

(defn exit          []  (.exit         Gdx/app))
(defn post-runnable [f] (.postRunnable Gdx/app f))

(defn ->application-listener [& {:keys [create
                                        resize
                                        render
                                        pause
                                        resume
                                        dispose]}]
  (proxy [ApplicationListener] []
    (create []
      (when create
        (create)))

    (resize [width height]
      (when resize
        (resize width height)))

    (render []
      (when render
        (render)))

    (pause []
      (when pause
        (pause)))

    (resume []
      (when resume
        (resume)))

    (dispose []
      (when dispose
        (dispose)))))
