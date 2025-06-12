(ns lupapalvelu.ui.location.bim-viewer.viewer-xeokit
  "Namespace for wrangling the xeokit-sdk's JavaScript plugins for BIM viewer
  behind a simple API."
  (:require ["@xeokit/xeokit-sdk/src/extras/ContextMenu" :refer [ContextMenu]]
            ["@xeokit/xeokit-sdk/src/plugins/DistanceMeasurementsPlugin/DistanceMeasurementsMouseControl" :refer [DistanceMeasurementsMouseControl]]
            ["@xeokit/xeokit-sdk/src/plugins/DistanceMeasurementsPlugin/DistanceMeasurementsPlugin" :refer [DistanceMeasurementsPlugin]]
            ["@xeokit/xeokit-sdk/src/plugins/NavCubePlugin" :refer [NavCubePlugin]]
            ["@xeokit/xeokit-sdk/src/plugins/TreeViewPlugin" :refer [TreeViewPlugin]]
            ["@xeokit/xeokit-sdk/src/plugins/XKTLoaderPlugin" :refer [XKTLoaderPlugin]]
            ["@xeokit/xeokit-sdk/src/viewer/scene/CameraControl/CameraControl" :refer [CameraControl]]
            ["@xeokit/xeokit-sdk/src/viewer/Viewer" :refer [Viewer]]
            [goog.object :as googo]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.location.bim-viewer.viewer-config :as bim-viewer-config]
            [lupapalvelu.ui.location.bim-viewer.viewer-subs :as bim-viewer-subs]
            [sade.strings :as ss]
            [sade.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Plugin JS interop

(defn- entity-by-id [{:keys [viewer scene]} entity-id]
  (some-> (or scene (some-> viewer .-scene))
          .-objects
          (googo/get entity-id)))

(defn- move-camera-to! [ctx target]
  ;; NOTE:; There is a few references to Xeokit types here in the inference hints,
  ;; we don't need to import the actual file for these, the ^js hint is enough.
  (when-let [^js/CameraFlightAnimation camera-flight (some-> ctx :viewer .-cameraFlight)]
    (.flyTo camera-flight target)))

(defn reset-camera!
  "Moves camera to the default position"
  [ctx]
  (let [^js/SceneModel model (-> ctx :models last)
        ^Viewer viewer    (:viewer ctx)
        ^js/Camera camera    (some-> viewer .-camera)]
    (set! (.-eye camera) (clj->js [0 0 10]))
    (set! (.-look camera) (clj->js [0 0 0]))
    (set! (.-up camera) (clj->js [0 1 0]))
    (some->> model (move-camera-to! ctx))))

(defn toggle-projection!
  "Toggles viewer projection between perspective and orthographic"
  [ctx]
  (when-let [^Camera camera (some-> ctx :viewer .-camera)]
    (set! (.-projection camera)
          (if (= "ortho" (.-projection camera))
            "perspective"
            "ortho"))))

(defn- hovered-entity
  "Returns the object in the viewer the mouse cursor is currently hovering over"
  [ctx js-event]
  (some-> (or (:scene ctx)
              (some-> ctx :viewer .-scene))
          (.pick (clj->js {:canvasPos [(.-layerX js-event) (.-layerY js-event)]}))
          .-entity))

(defn- open-context-menu!
  "Opens the context-sensitive menu for the currently right-clicked thing"
  [{:keys [context-menu] :as ctx} js-event & [node]]
  (when (some-> context-menu .-enabled)
    (let [entity (if node
                   (entity-by-id ctx (.-objectId node))
                   (hovered-entity ctx js-event))]
      (when (or entity node)
        (set! (.-context context-menu) (clj->js {:entity entity
                                                 :node   node}))
        (.show context-menu
               (+ (.-pageX js-event) 20) ; Move context menu out from under the cursor
               (.-pageY js-event)))))
  (.preventDefault js-event))

(defn- clear-selected-object! [^Scene scene ^TreeViewPlugin tree-view]
  (when scene
    (.setObjectsSelected scene (.-selectedObjectIds scene) false))
  (some-> tree-view .unShowNode))

(defn- set-selected-object! [^Scene scene ^TreeViewPlugin tree-view entity-id]
  (when-let [id-array (some-> entity-id vector clj->js)]
    (some-> scene (.setObjectsSelected id-array true))
    (some-> tree-view (.showNode entity-id))))

(defn- set-measuring-mode! [ctx value]
  (when-let [control ^DistanceMeasurementsMouseControl (:measurements-control ctx)]
    (if value
      (.activate ^DistanceMeasurementsMouseControl control)
      (.deactivate ^DistanceMeasurementsMouseControl control))
    ;; Remove existing measurements when deactivating
    (when-not value
      (some-> control .-distanceMeasurementsPlugin .clear))
    (>evt [::bim-viewer-subs/set-mode? :measuring-mode? value])))

(defn- set-picking-mode! [ctx value]
  (let [^TreeViewPlugin tree-view (:tree-view ctx)
        ^Scene scene              (some-> ctx :viewer .-scene)]
    (when-not value
      (clear-selected-object! scene tree-view))
  (>evt [::bim-viewer-subs/set-mode? :picking-mode? value])))

(def mode-key->set-mode-fn!
  {:measuring-mode? set-measuring-mode!
   :picking-mode?   set-picking-mode!})

(defn- set-mode!
  "Turns on or off an interaction mode that changes what the user's clicks on the viewer model(s) do.
  Toggle if new-value is nil"
  [ctx mode-key new-value]
  (let [set-mode-fn! (get mode-key->set-mode-fn! mode-key)]
    (if (nil? new-value)
      (set-mode-fn! ctx (-> ctx mode-key not))
      (set-mode-fn! ctx new-value))))

(defn toggle-mode!
  "Generic interaction mode toggler for wrangling the different implementations.
  Disables all other modes and toggles on/off or sets the given mode"
  [ctx target-mode-key & [enabled?]]
  (doseq [mode-key (keys mode-key->set-mode-fn!)]
    (if (= mode-key target-mode-key)
      (set-mode! ctx mode-key enabled?)
      (set-mode! ctx mode-key false))))

(defn set-pointer-enabled!
  "Enables the mouse pointer functionality on mouse release, if it was disabled during a click
  in one of the interaction modes"
  [ctx value]
  (when-let [^CameraControl camera-control (some-> ctx :viewer .-cameraControl)]
    (set! (.-pointerEnabled camera-control) value)))

(defn select-hovered-entity!
  "Highlights the entity currently hovered over both in the model and in the tree view"
  [ctx js-event]
  (let [picking?                  (:picking-mode? ctx)
        ^Scene scene              (some-> ctx :viewer .-scene)
        ^TreeViewPlugin tree-view (:tree-view ctx)
        canvas-style              (some-> scene .-canvas .-canvas .-style)
        entity-id                 (some-> (hovered-entity ctx js-event) .-id)]
    (when (and picking? entity-id)
      (set-pointer-enabled! ctx false) ; Disable mouse navigation during picking click (enabled in on-mouse-up)
      (when canvas-style (set! (.-cursor canvas-style) "default"))
      (clear-selected-object! scene tree-view)
      (set-selected-object! scene tree-view entity-id)
      (>evt [::bim-viewer-subs/set-selected-entity-id entity-id]))))

(defn select-tree-hierarchy!
  "Sets the selected organizing principle for the tree view of the models' parts; e.g. by IFC type"
  [ctx value]
  (when-let [^TreeViewPlugin tree-view (:tree-view ctx)]
    (set! (.-hierarchy tree-view) value))
  (>evt [::bim-viewer-subs/set-selected-tree-hierarchy value]))

(defn- context-menu-items
  "The available options in the context-sensitive menu that shows up when right-clicking on things"
  [{:keys [scene tree-view] :as ctx}]
  (let [has-entity? #(some-> % .-entity some?)]
    [[;; Move camera to targeted object
      {:getTitle   #(common/loc :application.bim.viewer.menu.fly-to)
       :getShown   has-entity?
       :doAction   #(some->> % .-entity (move-camera-to! ctx))}
      ;; Select the object, highlighting it in the scene and the tree-view
      {:getTitle   #(common/loc :application.bim.viewer.menu.select)
       :getShown   has-entity?
       :getEnabled #(some-> % .-entity .-selected not)
       :doAction   #(when-let [entity-id (some->> % .-entity .-id)]
                      (clear-selected-object! scene tree-view)
                      (set-selected-object! scene tree-view entity-id))}
      ;; Toggle the object's visibility
      {:getTitle   #(if (some-> % .-entity .-visible)
                      (common/loc :application.bim.viewer.menu.hide)
                      (common/loc :application.bim.viewer.menu.show))
       :getShown   has-entity?
       :doAction   #(when-let [entity (some-> % .-entity)]
                      (->> (.-visible entity)
                           not
                           (set! (.-visible entity))))}]]))

(defn- xkt-file-path [application-id ifc-version-id]
  (->> {"id"             application-id
        "ifc-version-id" ifc-version-id}
       (map #(ss/join "=" %))
       (ss/join "&")
       (str "/api/raw/download-xkt-file?")))

(defn load-xkt-model
  "Fetches the XKT file for the given IFC file (presuming it has been generated) and loads it as
  a view model for the viewer"
  [viewer {:keys [application-id ifc-version-id]}]
  (let [xkt-loader (XKTLoaderPlugin. viewer)
        model      (some->> (xkt-file-path application-id ifc-version-id)
                            (bim-viewer-config/model-opts ifc-version-id)
                            clj->js
                            (.load xkt-loader))
        ;; Does not include other models (or other data) because only used for positioning the camera
        camera-ctx {:viewer viewer
                    :models [model]}]
    ;; Move the camera to show the added model
    (some-> model (.on "loaded" #(reset-camera! camera-ctx)))
    (>evt [::bim-viewer-subs/model-loaded model])))

(defn init-bim-viewer!
  "Sets up the xeokit-sdk Viewer and loads the XKT file for display in it"
  [ifc-info]
  (let [viewer               (Viewer. (clj->js bim-viewer-config/viewer-opts))
        tree-view            (TreeViewPlugin. viewer (clj->js (bim-viewer-config/tree-opts)))
        context-menu         (ContextMenu. (clj->js bim-viewer-config/context-menu-opts))
        measurements         (DistanceMeasurementsPlugin. viewer (clj->js (bim-viewer-config/measurement-opts)))
        measurements-control (DistanceMeasurementsMouseControl. measurements)
        _navcube             (NavCubePlugin. viewer (clj->js bim-viewer-config/navcube-opts))
        scene                (some-> viewer .-scene)
        canvas               (some-> scene .-canvas .-canvas)
        ctx                  {:viewer       viewer
                              :scene        scene
                              :tree-view    tree-view
                              :context-menu context-menu}]
    ;; Enable context-menu (right-clicking on things)
    (when canvas (set! (.-oncontextmenu canvas) #(open-context-menu! ctx %)))
    (some-> tree-view (.on "contextmenu" #(open-context-menu! ctx (.-event %) (.-treeViewNode %))))
    (when context-menu (set! (.-items context-menu) (clj->js (context-menu-items ctx))))
    ;; Fix bug in NavCube mouse position calculation by hacking the canvas offset
    ;; The magic number 65 found by trial and error; does not seem to derive from anything in the page
    (when-let [navcube-canvas (some-> bim-viewer-config/navcube-opts :canvasId js/document.getElementById)]
      (js/Object.defineProperty navcube-canvas "offsetTop" (clj->js {:get (constantly 65)})))
    ;; Set up localizations
    (when-let [locale-service (some-> viewer .-localeService)]
      (.loadMessages locale-service (clj->js (bim-viewer-config/locale-service-opts)))
      (set! (.-locale locale-service) "default"))
    ;; Save the various plugin JS objects for later
    (>evt [::bim-viewer-subs/init
           (partial load-xkt-model viewer)
           ifc-info
           (util/mmap viewer tree-view measurements-control)])))
