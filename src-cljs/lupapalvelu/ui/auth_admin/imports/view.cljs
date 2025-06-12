(ns lupapalvelu.ui.auth-admin.imports.view
  "Authority admin Excel imports section root view. Each individual import configuration has
  their own view (routed by config id)."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.auth-admin.imports.config :as cfg-view]
            [lupapalvelu.ui.auth-admin.imports.data.core :as dcore]
            [lupapalvelu.ui.auth-admin.imports.data.view :as dview]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.imports.data.core :as idata]))

(defn config-row
  "Configuration row. The configuration can be opened by clicking its title."
  [cfg-id]
  (let [deleted?     (<sub [::dview/include-deleted?])
        {:keys [filename modified imported deleted]
         :as   info} (<sub [::dview/config-info cfg-id])]
    [:tr
     [:td [components/click-link {:text  (idata/get-title info)
                                  :click #(>evt [::dcore/goto-config cfg-id])}]]
     [:td.font-mono filename]
     [:td (some-> imported date/finnish-date)]
     [:td (some-> modified date/finnish-date)]
     (when deleted?
       [:td (some-> deleted date/finnish-date)])]))

(defn configurations
  "Table of existing configurations (imported or not)."
  []
  (when (<sub [::dview/configs?])
    (let [deleted? (<sub [::dview/include-deleted?])
          cfg-ids  (<sub [::dview/config-ids])]
      [:table
       [:thead
        (into [:tr] (for [k (cond-> [:map.name :imports.excel :imports.imported
                                     :pate.verdict-table.last-edit]
                              deleted? (conj :imports.config.deleted))]
                      [:th (path/loc k)]))]
       (into [:tbody]
             (for [cfg-id cfg-ids]
                    [config-row cfg-id]))])))

(defn include-deleted
  "Show deleted toggle is shown only if any of the configuration is marked deleted."
  []
  (when (<sub [::dview/any-deleted?])
    [components/toggle (<sub [::dview/include-deleted?])
     {:text-loc :handler-roles.show-all
      :callback #(>evt [::dview/toggle-deleted %])}]))

(defn view
  []
  (when (<sub [::dview/visible?])
    [:div.flex--column.gap--v2
     [:div.flex--between.flex--column-gap2.flex--align-center
      [:h2 (common/loc :imports.title)]
      [include-deleted]]
     [configurations]
     [cfg-view/error-note]
     [cfg-view/file-picker]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
