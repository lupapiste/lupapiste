(ns lupapalvelu.ui.location.bim-viewer.viewer-config
  "Configuration options and default values for the BIM viewer and its plugins"
  (:require [lupapalvelu.ui.common :as common]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI component configuration options

(def tree-hierarchy-options
  "The object tree can display the objects in a couple different ways:
  - storeys = grouped by storeys [sic] under buildings
  - types = grouped IFC types (e.g. windows, pillars, etc.)"
  [{:value   "storeys"
    :i18nkey "application.bim.viewer.hierarchy.storeys"}
   {:value   "types"
    :i18nkey "application.bim.viewer.hierarchy.types"}])

(def default-hierarchy
  (-> tree-hierarchy-options first :value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Plugin configuration options

(def colors
  "Lupapiste default colors defined in SASS variables of the same name"
  {:violet-20  "#dccee9"
   :violet-60  "#956bbd"
   :violet-100 "#4f0891"
   :light-gray "#ecedef"})

(def viewer-opts
  {:canvasId    "bim-viewer-canvas"
   :transparent true
   :dtxEnabled  true})

(defn tree-opts []
  {:containerElement (js/document.getElementById "bim-viewer-tree")
   :hierarchy        default-hierarchy
   :pruneEmptyNodes  true
   :autoExpandDepth  1})

(def context-menu-opts
  ":items are actually added in bim-viewer-xeokit since they call xeokit-sdk functions and depend on other plugins"
  {:enabled true
   :items   []})

(defn measurement-opts []
  {:defaultAxisVisible  true
   :container           (js/document.getElementById "bim-viewer-container")
   :defaultColor        (get colors :violet-100)
   :snapToVertex        true
   :snapToEdge          true})

(def navcube-opts
  {:canvasId          "bim-viewer-navcube-canvas"
   :shadowVisible     false
   :cameraFly         true
   :cameraFlyDuration 0.5
   :color             (get colors :violet-20)
   :frontColor        (get colors :violet-100)
   :backColor         (get colors :violet-100)
   :leftColor         (get colors :violet-60)
   :rightColor        (get colors :violet-60)})

(defn model-opts [id src]
  {:id                 id
   :src                src
   :edges              true
   :saoEnabled         true
   :dtxEnabled         true
   :globalizeObjectIds true ; fixes duplicate models breaking plugins
   :objectDefaults     {:IfcWindow {:opacity 0.3}}})

(defn locale-service-opts
  "A map of xeokit-sdk viewer localization keys to localized strings.
  Changing the Lupapiste language causes these to be refreshed so no need to make maps for fi and sv separately."
  []
  {:default {:NavCube {:front  (common/loc :application.bim.viewer.navcube.front)
                       :back   (common/loc :application.bim.viewer.navcube.back)
                       :top    (common/loc :application.bim.viewer.navcube.top)
                       :bottom (common/loc :application.bim.viewer.navcube.bottom)
                       :left   (common/loc :application.bim.viewer.navcube.left)
                       :right  (common/loc :application.bim.viewer.navcube.right)}}})
