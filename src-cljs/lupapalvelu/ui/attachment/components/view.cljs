(ns lupapalvelu.ui.attachment.components.view
  (:require [lupapalvelu.ui.common :as common :refer [loc]]
            [rum.core :as rum]))

(rum/defc view-with-download < {:key-fn #(str "view-with-download-" (:id %))}
  "Port of ko.bindingHandlers.viewWithDownload"
  [{:keys [id latestVersion]}]
  [:div.view-with-download
   [:a {:target "_blank"
        :href (str "/api/raw/latest-attachment-version?attachment-id=" id)}
    (:filename latestVersion)]
    [:br]
    [:div.download
     [:a {:href (str "/api/raw/latest-attachment-version?download=true&attachment-id=" id)}
      [:i.lupicon-download.btn-small]
      [:span (common/loc "download-file")]]]])

(rum/defc view-with-download-small-inline < {:key-fn #(str "view-with-download-inline-" (:id %))}
  [{:keys [id latestVersion]} & [options]]
  [:div.inline
   [:a {:target "_blank"
        :href (str "/api/raw/latest-attachment-version?attachment-id=" id)}
    (if (:icon-only? options)
      [:i.lupicon-download.btn-small]
      (:filename latestVersion))]])
