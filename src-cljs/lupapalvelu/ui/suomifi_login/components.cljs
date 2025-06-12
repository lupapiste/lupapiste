(ns lupapalvelu.ui.suomifi-login.components
  (:require [lupapalvelu.ui.common :as common]))

(defn logout-button
  [{:keys [info-loc button-class]
    :or   {info-loc     :ident.logout.info-text-initial
           button-class :primary}}]
  [:<>
   [:p (common/loc info-loc)]
   [:button
    {:class    button-class
     :on-click (fn [_]
                 (js/util.identLogoutRedirect))}
    (common/loc :ident.logout.action)]])
