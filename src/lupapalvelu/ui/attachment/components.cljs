(ns lupapalvelu.ui.attachment.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as jsutil]))

(rum/defc upload-link [id]
  (let [input-id (or id (jsutil/unique-elem-id))]
    [:div.inline
     [:input.hidden {:type "file"
                     :name "files[]"
                     :id input-id}]
     [:a.link-btn.link-btn--link
      [:label {:for input-id}
       [:i.lupicon-circle-plus]
       [:i.wait.spin.lupicon-refresh]
       [:span (js/loc "attachment.addFile")]]]]))

(rum/defc view-with-download < {:key-fn (fn [version] (:fileId version))}
  "Port of ko.bindingHandlers.viewWithDownload"
  [latest-version]
  [:div.view-with-download
   [:a {:target "_blank"
        :href (str "/api/raw/view-attachment?attachment-id=" (:fileId latest-version))}
    (:filename latest-version)]
    [:br]
    [:div.download
     [:a {:href (str "/api/raw/download-attachment?attachment-id=" (:fileId latest-version))}
      [:i.lupicon-download.btn-small]
      [:span (js/loc "download-file")]]]])
