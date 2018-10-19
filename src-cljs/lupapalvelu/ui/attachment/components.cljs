(ns lupapalvelu.ui.attachment.components
  (:require [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.util :as jsutil]
            [rum.core :as rum]))

(defn- destroy-file-upload-subscriptions [state]
  (hub/send "fileuploadService::destroy" {:input (::input-id state)})
  (doseq [id (vals (::fileupload-subscription-ids state))]
    (hub/unsubscribe id))
  true)

(defn upload-mixin
  "The first component argument is either callback function or options map.
  Options [optional]:

    callback: callback function or map. See upload/service-hubscribe
              for details.

    [multiple?]: whether multiple files can be selected/uploaded.

    [dropzone]:  dropzone container (!) selector that will be passed to
                 $() on the jQuery side. Note: the selector is for the
                 some element that (ultimately) contains the drop-zone
                 element.

    [input-id]: (hidden) file input-id. Default is generated unique id."
  []
  (letfn [(parse-args [state]
            (let [[args] (:rum/args state)]
              (if (map? args)
                (select-keys args [:callback :dropzone :input-id :multiple?])
                {:callback args})))
          (initialize [state]
            (let [{:keys [callback dropzone multiple?]} (parse-args state)
                  input-id                              (::input-id state)]
              (upload/bindToElem (js-obj "id" input-id
                                         "dropZone" dropzone
                                         "allowMultiple" (boolean multiple?)))
              (assoc state
                     ::fileupload-subscription-ids (upload/service-hubscribe input-id
                                                                             callback))))]
    {:will-mount (fn [state]
                   (assoc state ::input-id (get (parse-args state)
                                                :input-id
                                                (jsutil/unique-elem-id "file-upload-"))))

     :did-mount initialize

     :should-update (fn [old new]
                      (let [{old-callback :callback} (parse-args old)
                            {new-callback :callback} (parse-args new)]
                        (if (or (not= (::input-id old) (::input-id new))
                                (not= old-callback new-callback))
                          (destroy-file-upload-subscriptions old)
                          false)))

     :did-update initialize

     :will-unmount (fn [state]
                     (destroy-file-upload-subscriptions state)
                     (dissoc state ::input-id)
                     state)}))

(rum/defcs upload-link < (upload-mixin)
  "Handles file upload with fileupload-service.
  First param must be callback function, which receives filedata event via hub."
  [local-state _ & _]
  [:div.inline
   [:input.hidden {:type "file"
                   :name "files[]"
                   :data-test-id "upload-link-input"
                   :id (::input-id local-state)}]
   [:a.link-btn.link-btn--link
    [:label {:for (::input-id local-state)
             :data-test-id "upload-link"}
     [:i.lupicon-circle-plus]
     [:i.wait.spin.lupicon-refresh]
     [:span (common/loc "attachment.addFile")]]]])

(defn dropzone
  "ClojureScript version of the legacy (Knockout) DropZone component."
  []
  [:drop-zone
   [:div.drop-zone-placeholder
    [:i.lupicon-upload]
    [:div (common/loc "dropzone.placeholder")]]])

(rum/defcs upload-wrapper < (upload-mixin)
  "Convenience wrapper file upload components. The idea is that the
  wrapper takes care of the mundane upload details and provides the
  wrapped component with the necessary upload-options.

  Wrapper options [optional]. In addition to upload-mixin options (see
  above):
    :component Wrapped component
    [:test-id]   Test id (prefix).

  The wrapped component receives the following options as the first
  argument:

    :callback Callback function (pass-through from wrapper)
    :test-id  Pass-through from wrapper
    :input    Hidden file input (test-id is wrapper-test-id-option-input)
    :input-id Id of the hidden file input.

  The other-args are passed as is to the wrapped component."
  [{input-id ::input-id} {:keys [component test-id multiple?] :as options} & other-options]
  (apply component
         (-> options
                   (dissoc :component)
                   (assoc :input [:input.hidden
                                  (merge {:type "file"
                                          :name "files[]"
                                          :id input-id}
                                         (when test-id
                                           {:data-test-id (common/test-id test-id
                                                                          :input)})
                                         (when multiple?
                                           {:multiple true}))]
                          :input-id input-id))
         other-options))

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
  [{:keys [id latestVersion]}]
  [:div.inline
   [:a {:target "_blank"
        :href (str "/api/raw/latest-attachment-version?attachment-id=" id)}
    (:filename latestVersion)]])

(defn delete-with-confirmation [attachment]
  (hub/send  "show-dialog"
             {:ltitle          "attachment.delete.header"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:ltext (if (or (not-empty (:versions attachment)) (:latestVersion attachment))
                                         "attachment.delete.message"
                                         "attachment.delete.message.no-versions")
                                :yesFn #(js/lupapisteApp.services.attachmentsService.removeAttachment (:id attachment))}}))

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
  [:div.inline.right
   [:a {:on-click #(delete-with-confirmation attachment)
        :data-test-id "delete-attachment-link"}
    (str "[" (common/loc "remove") "]")]])
