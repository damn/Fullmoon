(ns gdx.scene2d.stage
  (:import com.badlogic.gdx.scenes.scene2d.Stage))

(defn act!       [^Stage stage]       (.act      stage))
(defn draw       [^Stage stage]       (.draw     stage))
(defn root       [^Stage stage]       (.getRoot  stage))
(defn clear!     [^Stage stage]       (.clear    stage))
(defn add-actor! [^Stage stage actor] (.addActor stage actor))

(defn add-actors! [^Stage stage actors]
  (run! #(add-actor! stage %) actors))

;	/** Returns the {@link Actor} at the specified location in stage coordinates. Hit testing is performed in the order the actors
;	 * were inserted into the stage, last inserted actors being tested first. To get stage coordinates from screen coordinates, use
;	 * {@link #screenToStageCoordinates(Vector2)}.
;	 * @param touchable If true, the hit detection will respect the {@link Actor#setTouchable(Touchable) touchability}.
;	 * @return May be null if no actor was hit. */
(defn hit [^Stage stage [x y] & {:keys [touchable?]}]
  (.hit stage x y touchable?))
