(ns lupapalvelu.ui.docgen.field.party-attachment
  (:require [goog.object :as gobj]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.attachment.components.upload-zone :as upload-zone]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.attachments :as attachments]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [reagent.core :as r]
            [schema.core :as sc]))

(defn refresh-validation-and-auth [app-id doc-id schema path]
  (let [emit-data     (emit/schema->emit-data {:app-id app-id
                                               :doc-id doc-id
                                               :schema schema})
        value         nil
        callback-args {:app-id      app-id
                       :doc-id      doc-id
                       :field-paths [path]
                       :values      [value]
                       :emit-data   emit-data}]
    (>evt [::docgen-common/change-immediate (assoc callback-args
                                                   :on-success [::docgen-common/on-save-success callback-args]
                                                   :on-error [::docgen-common/on-save-error callback-args])])))

(defn add-attachment [app-id doc-id schema path file-id pending]
  (let [{:keys [attachment-type-id attachment-target-type]} schema
        status-subscribe (atom nil)
        ^js att (.. js/lupapisteApp -services -attachmentsService)
        job-status (.bindAttachments
                     att
                     #js [#js {:fileId file-id
                               :contents (common/loc (str "attachmentType.osapuolet." attachment-type-id))
                               :group #js {:groupType "parties"}
                               :type #js {:type-id attachment-type-id
                                          :type-group "osapuolet"}
                               :target #js {:type attachment-target-type
                                            :id doc-id}}])]
    ;; Wait for the bind job to finish
    (reset! status-subscribe
            (.subscribe (gobj/get job-status file-id)
                        (fn [new-status]
                          (when (= (.-JOB_DONE att) new-status)
                            ;; Need to wait for the queryAll triggered by JOB_DONE status to also finish,
                            ;; so there is no flash of empty field when the new attachment data is not yet available.
                            (.subscribe js/hub #js {:eventType "attachmentsService::query"
                                                    :query "attachments"
                                                    :key "attachments"}
                                        (fn []
                                          (reset! pending false)
                                          (refresh-validation-and-auth app-id doc-id schema path)
                                          (.dispose ^js @status-subscribe))
                                        ;; oneshot
                                        true)))))))

(defn remove-attachment [app-id doc-id schema path attachment-id]
  (let [att (.. js/lupapisteApp -services -attachmentsService)]
    (.setTarget att attachment-id #js {})
    (.subscribe js/hub #js {:eventType "attachmentsService::update"
                            :commandName "set-attachment-target"
                            :id attachment-id}
                (fn []
                  (refresh-validation-and-auth app-id doc-id schema path))
                ;; oneshot
                true)))

(sc/defn render
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (r/with-let [pending (r/atom false)]
    (let [{:keys [attachment-target-type]} schema
          input-id (path/path->id path doc-id)
          {:keys [label]} (di/args->label args)

          current-attachment (<sub [::attachments/get-by-target attachment-target-type doc-id])]
      [:div.form-entry
       [di/docgen-label
        {:id input-id
         :label label}]
       [:div.party-attachment-input
        [:div.text-sm.party-attachment-input__value
         {:class (when-not @pending "hidden")}
         [:i.lupicon-refresh.icon-spin.text-sm
          {:title ""}]
         [:span.ml-2
          (common/loc "progress.add.one")]]
        ;; Important to keep the upload-zone component in the tree while
        ;; upload is pending, else the on-success handler is also removed!
        [:div
         {:class (when @pending "hidden")}
         (if current-attachment
           [:div.osapuoli-attachment-input__value
            [:div (or (some-> current-attachment :latestVersion :filename)
                      "-")]
            (when-not disabled?
              [:button.secondary
               {:on-click (fn [_e]
                            (remove-attachment app-id doc-id schema path (:id current-attachment)))}
               (common/loc "attachment.delete")])]
           (when-not disabled?
             [:f> upload-zone/upload-zone-new
              {:input-id input-id
               :single-file true
               :on-pending (fn [_e]
                             ;; filesUploading - pending
                             (reset! pending true))
               :on-failed (fn [_e]
                            ;; filesUploaded - failed
                            (reset! pending false))

               :on-success (fn [e]
                             ;; filesUploaded - success
                             (let [file-id (-> e :files (nth 0) :fileId)]
                               (add-attachment app-id doc-id schema path file-id pending)))
               :bucket nil}]))]]])))
