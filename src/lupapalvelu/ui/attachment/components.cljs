(ns lupapalvelu.ui.attachment.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.rum-util :as rum-util]))

(rum/defc upload-link [id]
  (let [input-id (or id (jsutil/unique-elem-id))]
    [:div.inline
     [:input.hidden {:type "file"
                     :name "files[]"
                     :data-test-id "upload-link-input"
                     :id input-id}]
     [:a.link-btn.link-btn--link
      [:label {:for input-id
               :data-test-id "upload-link"}
       [:i.lupicon-circle-plus]
       [:i.wait.spin.lupicon-refresh]
       [:span (js/loc "attachment.addFile")]]]]))

(rum/defc view-with-download < {:key-fn #(str "view-with-download-" (:fileId %))}
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

(rum/defc view-with-download-small-inline < {:key-fn #(str "view-with-download-inline-" (:fileId %))}
  [latest-version]
  [:div.inline
   [:a {:target "_blank"
        :href (str "/api/raw/view-attachment?attachment-id=" (:fileId latest-version))}
    (:filename latest-version)]])

(rum/defc delete-attachment-link < {:key-fn #(str "delete-attachment-" (:id %1))}
                                   (rum-util/hubscribe "attachmentsService::remove"
                                                       (fn [state]
                                                         {:commandName "delete-attachment"
                                                          :ok          false
                                                          :attachmentId (-> state :rum/args first :id)})
                                                       (fn [_ resp] (.showSavedIndicator js/util resp)))
                                   (rum-util/hubscribe "attachmentsService::remove"
                                                       (fn [state]
                                                         {:commandName "delete-attachment"
                                                          :ok          true
                                                          :attachmentId (-> state :rum/args first :id)})
                                                       (fn [state resp] (let [[_ callback] (-> state :rum/args)] (callback resp))))
  [attachment _]
  (letfn [(remove-attachment   [_] (.removeAttachment js/lupapisteApp.services.attachmentsService (:id attachment)))
          (delete-confirmation [_]
            (.send js/hub
                   "show-dialog"
                   #js {:ltitle          "attachment.delete.header"
                        :size            "medium"
                        :component       "yes-no-dialog"
                        :componentParams #js {:ltext (if (or (not-empty (:versions attachment)) (:latestVersion attachment))
                                                       "attachment.delete.message"
                                                       "attachment.delete.message.no-versions")
                                              :yesFn remove-attachment}}))]
    [:div.inline.right
     [:a {:on-click delete-confirmation
          :data-test-id "delete-attachment-link"}
      (str "[" (js/loc "remove") "]")]]))
