(ns lupapalvelu.ui.auth-admin.edit-authority.edit-view
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-info :as info]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as roles]
            [lupapalvelu.ui.auth-admin.edit-authority.subs :as edit-authority-subs]
            [goog.object :as googo]
            [lupapalvelu.ui.dom :as ui-dom]
            [sade.strings :as ss]))

(defn edit-authority-view []
  (if (<sub [::edit-authority-subs/authority])
    [:div
     [:h1 (ss/join-non-blanks ", " [(common/loc "auth-admin.edit-authority.title") (<sub [::edit-authority-subs/authority-name])])]
     [roles/edit-authority-roles]
     [:div.hr]
     [info/edit-authority-info]]
    [:div (str (common/loc "loading") "...")]))

(defonce mounter (ui-dom/init :reagent (fn [] edit-authority-view)))

(defn ^:export start [dom-id params]
  (>evt [::edit-authority-subs/fetch-organization])
  (>evt [::edit-authority-subs/fetch-authority ((googo/get params "authorityId"))])
  (ui-dom/mount! mounter dom-id))
