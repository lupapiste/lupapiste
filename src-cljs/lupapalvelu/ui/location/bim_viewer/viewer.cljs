(ns lupapalvelu.ui.location.bim-viewer.viewer
  "BIM viewer allows the user to look at and interact with a 3D representation of an IFC model.
  A lot like map-component but for 3D buildings instead of 2D maps.

  The namespaces within here have the following functions:
  - only bim-viewer-xeokit is allowed to interop with the xeokit-sdk plugins
  - only bim-viewer (this namespace) is allowed to use <sub:s
  - only bim-viewer-subs is allowed to define re-frame subscriptions and events
  This ensures the data flows (viewer -> xeokit -> subs -> viewer) in a way that re-frame likes"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.location.bim-viewer.viewer-config :as bim-viewer-config]
            [lupapalvelu.ui.location.bim-viewer.viewer-subs :as bim-viewer-subs]
            [lupapalvelu.ui.location.bim-viewer.viewer-xeokit :as bim-viewer-xeokit]
            [reagent.core :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Views

(defonce prevent-scroll-fn #(.preventDefault %)) ; defonce to prevent DOM updates breaking scrolling

(defn- bim-viewer-canvas []
  ;; Get the ctx here, so we don't have to use re-frame subscribes inside the toggle functions
  (let [ctx {:viewer               (<sub [::bim-viewer-subs/viewer])
             :tree-view            (<sub [::bim-viewer-subs/tree-view])
             :measurements-control (<sub [::bim-viewer-subs/measurements-control])
             :hovered-entity-id    (<sub [::bim-viewer-subs/hovered-entity-id])
             :selected-entity-id   (<sub [::bim-viewer-subs/selected-entity-id])
             :picking-mode?        (<sub [::bim-viewer-subs/picking-mode?])}]
    [:canvas#bim-viewer-canvas
     {:class          [(when (<sub [::bim-viewer-subs/fullscreen?])
                         "bim-viewer-canvas-fullscreen")]
      ;; on-mouse-down and -up so we can disable rotation during the picking
      :on-mouse-down  #(bim-viewer-xeokit/select-hovered-entity! ctx (.-nativeEvent %))
      :on-mouse-up    #(bim-viewer-xeokit/set-pointer-enabled! ctx true)
      ;; Prevent mouse wheel inside the viewer from also scrolling the page (in addition to zoom)
      :on-mouse-enter #(js/window.addEventListener "wheel" prevent-scroll-fn (clj->js {:passive false}))
      :on-mouse-leave #(js/window.removeEventListener "wheel" prevent-scroll-fn)}]))

(defn- bim-viewer-tree
  "Displays a tree of the objects in the model.
  The tree has two organizing options (hierarchies), by storey or by IFC type.
  Individual nodes can be turned on or off, toggling their (and their children's) visibility"
  []
  ;; Get the ctx here, so we don't have to use re-frame subscribes inside the toggle functions
  (let [ctx {:tree-view (<sub [::bim-viewer-subs/tree-view])}]
    [:div.bim-viewer-tree-container
     ;; Toggle visibility to not break plugin integration since it is tied to the #bim-viewer-tree element
     ;; and also to retain state when toggling the view on or off
     {:style    {:visibility (if (<sub [::bim-viewer-subs/tree-view-open?]) "visible" "hidden")}
      :on-click #(.stopPropagation %)}
     [:div.flex.flex--column
      [:div.flex (common/loc :application.bim.viewer.hierarchy)]
      [:div.flex
       [components/select
        (<sub [::bim-viewer-subs/selected-tree-hierarchy])
        (map (fn [{:keys [value i18nkey]}]
               [value (common/loc i18nkey)])
             bim-viewer-config/tree-hierarchy-options)
        {:callback #(bim-viewer-xeokit/select-tree-hierarchy! ctx %)}]]]
     [:div#bim-viewer-tree]]))

(defn- bim-viewer-button [{:keys [icon i18nkey on-click disabled? mode-on?]}]
  [components/icon-button
   {:icon       icon
    :icon-only? true
    :class      (if mode-on? :primary.mode-on :primary)
    :disabled?  disabled?
    :title-loc  i18nkey
    :on-click   on-click}])

(defn- bim-viewer-buttons
  "A toolbar for toggling elements of the viewer and interaction modes (such as measurement) on/off
  as well as some camera controls"
  []
  ;; Get the ctx here, so we don't have to use re-frame subscribes inside the toggle functions
  (let [ctx {:viewer               (<sub [::bim-viewer-subs/viewer])
             :models               (<sub [::bim-viewer-subs/models])
             :measurements-control (<sub [::bim-viewer-subs/measurements-control])
             :measuring-mode?      (<sub [::bim-viewer-subs/measuring-mode?])
             :picking-mode?        (<sub [::bim-viewer-subs/picking-mode?])}]
    [:div.bim-viewer-button-container.flex.flex--row.flex--gap1
     [bim-viewer-button
      {:icon     :lupicon-refresh
       :i18nkey  :application.bim.viewer.buttons.reset
       :on-click #(bim-viewer-xeokit/reset-camera! ctx)}]
     [bim-viewer-button
      {:icon     :lupicon-eye
       :i18nkey  :application.bim.viewer.buttons.projection
       :on-click #(bim-viewer-xeokit/toggle-projection! ctx)}]
     [bim-viewer-button
      {:icon     :lupicon-document
       :i18nkey  :application.bim.viewer.buttons.tree-view
       :on-click #(>evt [::bim-viewer-subs/toggle-tree-view])}]
     [bim-viewer-button
      {:icon     :lupicon-measure
       :i18nkey  :application.bim.viewer.buttons.measuring
       :on-click #(bim-viewer-xeokit/toggle-mode! ctx :measuring-mode?)
       :mode-on? (:measuring-mode? ctx)}]
     [bim-viewer-button
      {:icon     :lupicon-search
       :i18nkey  :application.bim.viewer.buttons.picking
       :on-click #(bim-viewer-xeokit/toggle-mode! ctx :picking-mode?)
       :mode-on? (:picking-mode? ctx)}]
     [bim-viewer-button
      {:icon      :lupicon-circle-info
       :i18nkey   :application.bim.viewer.buttons.properties
       :on-click  #(>evt [::bim-viewer-subs/toggle-selection-details])
       :disabled? (nil? (<sub [::bim-viewer-subs/selected-entity-id]))}]
     [bim-viewer-button
      (let [fullscreen? (<sub [::bim-viewer-subs/fullscreen?])]
        {:icon      (if fullscreen?
                      :lupicon-collapse-up-left
                      :lupicon-expand-down-right)
         :i18nkey   (if fullscreen?
                      :application.bim.viewer.buttons.fullscreen-exit
                      :application.bim.viewer.buttons.fullscreen-enter)
         :on-click  #(>evt [::bim-viewer-subs/toggle-fullscreen])
         :disabled? false})]]))

(defn- bim-viewer-selection-details
  "Details on the currently selected model entity (e.g. a window)"
  []
  (when (<sub [::bim-viewer-subs/selection-details-open?])
    (when-let [selected (<sub [::bim-viewer-subs/selected-entity])]
      [:div.bim-viewer-selection-details-container
       [:span.title (.-name selected)]
       [:span (common/loc :application.bim.viewer.details.type) ": " (.-type selected)]])))

(defn- bim-viewer-navcube
  "A cube in the corner of the viewer that rotates with the model and shows which sides of the model are up, left, etc.
  Clicking on the cube will transport the camera to the respective view; e.g. clicking on the top side of the cube
  will move the camera to a top-down view."
  []
  [:canvas#bim-viewer-navcube-canvas])

(defn- bim-viewer-xkt-overlay
  "A layer that is displayed over the viewer when the XKT file needed for loading the model
  has not been generated (yet)"
 []
  (let [xkt-status      (<sub [::bim-viewer-subs/xkt-status])
        generating?     (= xkt-status "generating")
        file-size-error (<sub [::bim-viewer-subs/file-size-error])]
    (when (not= xkt-status "available")
      (if file-size-error
        [:div.bim-viewer-overlay
         [:div.mb-2 (common/loc :application.bim.viewer.ifc-file-size-error (common/sizeString (:max-size xkt-status) 0))]]
        [:div.bim-viewer-overlay
         [:div.mb-2
          (if generating?
            (common/loc :application.bim.viewer.xkt.generating)
            (common/loc :application.bim.viewer.xkt.failed))]
         (if generating?
           [:div [:i.icon-spin.lupicon-refresh]]
           [:button.primary.align--middle
            {:disabled (not (<sub [:auth/application? :generate-xkt-file]))
             :on-click #(>evt [::bim-viewer-subs/regenerate-xkt])}
            (common/loc :application.bim.viewer.xkt.retry-generation)])]))))

(defn- render-bim-viewer []
  [:div#bim-viewer-container
   {:class [(when (<sub [::bim-viewer-subs/fullscreen?])
              "bim-viewer-fullscreen")] }
   [bim-viewer-canvas]
   [bim-viewer-tree]
   [bim-viewer-buttons]
   [bim-viewer-selection-details]
   [bim-viewer-navcube]
   [bim-viewer-xkt-overlay]])

(defn bim-viewer [ifc-info]
  (r/create-class
    {:component-did-mount    #(bim-viewer-xeokit/init-bim-viewer! ifc-info)
     :component-will-unmount #(>evt [::bim-viewer-subs/clear-init])
     :display-name           "bim-viewer"
     :reagent-render         render-bim-viewer}))
