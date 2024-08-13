(ns gdx
  (:import com.badlogic.gdx.ApplicationListener))

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
