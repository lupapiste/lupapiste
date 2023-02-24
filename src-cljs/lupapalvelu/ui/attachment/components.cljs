(ns lupapalvelu.ui.attachment.components
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.common.upload :as upload]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.attachment.filters :as att-filters]
            [lupapalvelu.ui.attachment.grouping :as att-grouping]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.util :as jsutil]
            [rum.core :as rum]))


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
    {:init          (fn [state _]
                      (assoc state ::input-id (get (parse-args state) :input-id (jsutil/unique-elem-id "file-upload-"))))

     :did-mount     initialize

     :should-update (fn [old new]
                      (let [{old-callback :callback} (parse-args old)
                            {new-callback :callback} (parse-args new)]
                        (if (or (not= (::input-id old) (::input-id new))
                                (not= old-callback new-callback))
                          (upload/destroy-file-upload-subscriptions (::input-id old) (vals (::fileupload-subscription-ids old)))
                          false)))

     :did-update    initialize

     :will-unmount  (fn [{::keys [input-id fileupload-subscription-ids] :as state}]
                      (upload/destroy-file-upload-subscriptions input-id (vals fileupload-subscription-ids))
                      (dissoc state ::input-id)
                      state)}))

(rum/defcs upload-button < (upload-mixin)
  "Handles file upload with fileupload-service.
  First param must be callback function, which receives filedata event via hub."
  [local-state _ & _]
  [:div.inline
   [:input.hide-input {:type         "file"
                       :name         "files[]"
                       :data-test-id "upload-button-input"
                       :id           (::input-id local-state)}]
   [:label.tertiary {:for          (::input-id local-state)
                     :data-test-id "upload-button-label"}
    [:i.lupicon-circle-plus {:aria-hidden true}]
    [:span (common/loc "attachment.addFile")]]])

(defn dropzone
  "ClojureScript version of the legacy (Knockout) DropZone component."
  []
  [:drop-zone
   [:div.drop-zone-placeholder
    [:i.lupicon-upload]
    [:div (common/loc "dropzone.placeholder")]]])

(rum/defcs upload-wrapper < (upload-mixin)
                            rum/reactive ; maybe the 'component' uses rum/react, maybe not...
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
                   (assoc :input [:input.hide-input
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
  [{:keys [id latestVersion]} & [options]]
  [:div.inline
   [:a {:target "_blank"
        :href (str "/api/raw/latest-attachment-version?attachment-id=" id)}
    (if (:icon-only? options)
      [:i.lupicon-download.btn-small]
      (:filename latestVersion))]])

(defn- loc-escape [k]
  (js/_.escapeHTML (common/loc k)))

(defn delete-with-confirmation [attachment]
  (hub/send  "show-dialog"
             {:ltitle          "attachment.delete.header"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:text (if-let [latest (:latestVersion attachment)]
                                        (js/sprintf "%s: %s<br/>%s"
                                                    (loc-escape :attachment.delete)
                                                    (:filename latest)
                                                    (loc-escape :attachment.delete.message))
                                        (loc-escape "attachment.delete.message.no-versions"))
                                :yesFn #(js/lupapisteApp.services.attachmentsService.removeAttachment (:id attachment))}}))

(rum/defc delete-attachment-link < {:key-fn #(str "delete-attachment-" (:id %1))}
                                   (rum-util/hubscribe "attachmentsService::remove"
                                                       (fn [state]
                                                         {:commandName  "delete-attachment"
                                                          :ok           false
                                                          :attachmentId (-> state :rum/args first :id)})
                                                       (fn [_ resp] (.showSavedIndicator js/util resp)))
                                   (rum-util/hubscribe "attachmentsService::remove"
                                                       (fn [state]
                                                         {:commandName  "delete-attachment"
                                                          :ok           true
                                                          :attachmentId (-> state :rum/args first :id)})
                                                       (fn [state resp] (let [[_ callback] (-> state :rum/args)] (callback resp))))
  [attachment _]
  (components/click-link {:click   #(delete-with-confirmation attachment)
                          :test-id "delete-attachment-link"
                          :class   :gap--l1
                          :text    (str "[" (common/loc "remove") "]")}))

(defn download-button [attachments]
  (let [with-versions (filter (fn [{:keys [versions]}] (-> versions empty? not)) attachments)
        ac            (count with-versions)]
    (when (> ac 0)
      [:div [components/icon-button {:class    :tertiary
                                     :on-click #(.downloadAttachments js/lupapisteApp.services.attachmentsService
                                                                      (clj->js (map :id with-versions)))
                                     :icon     :lupicon-download
                                     :text     (str (loc "download") " " ac " " (loc (if (= ac 1) "file" "file-plural-partitive")))}]])))

(defn attachment-group
  "Renders given attachments in separate tables according to their main group (yleiset
  hankkeen liitteet, operation etc.)  and sub groups (pääpiirustukset, muut suunnitelmat
  etc.) Sub groups don't always exist and in those cases attachments are listed under the
  main group."
  [{:keys [group-display download? filter-set-key]} attachment-ids]
  (let [attachments (<sub [::shared/attachments-for-ids attachment-ids])
        tag-groups (-> (.tagGroups js/lupapisteApp.services.attachmentsService)
                       (js->clj :keywordize-keys true)
                       att-grouping/convert-tag-groups)]
    [:div
     (for [[main-group & sub-groups] tag-groups]
       (let [main-group-attachments    (when (empty? sub-groups)
                                         (att-grouping/attachments-by-group attachments
                                                                            [main-group]))
             all-sub-group-attachments (if (empty? main-group-attachments)
                                         (reduce (fn [acc [sg]]
                                                   (let [atts (att-grouping/attachments-by-group attachments
                                                                                                 [main-group sg])]
                                                     (if (empty? atts)
                                                       acc
                                                       (assoc acc sg atts))))
                                                 {}
                                                 sub-groups)
                                         {})]
         (when (or (not-empty main-group-attachments)
                   (not-empty all-sub-group-attachments))
           [:div
            {:key main-group}
            [:div.dsp--flex.flex--between.flex--align-center.gap--b1.flex--wrap
             [:h2.gap--b0 (att-grouping/group-loc main-group)]
             [att-filters/refresh-button filter-set-key]
             (when download?
               [download-button main-group-attachments])]
            (when (not-empty main-group-attachments)
              [group-display main-group-attachments])
            (for [[sub-group] (->> sub-groups
                                   (sort-by (comp (partial att-grouping/type-sort
                                                           att-grouping/type-group-order)
                                                  first)))
                  :let [sub-group-attachments (->> (all-sub-group-attachments sub-group)
                                                   (sort-by (comp (partial att-grouping/type-sort
                                                                           att-grouping/type-order)
                                                                  :type-id
                                                                  :type)))]]
              (when (not-empty sub-group-attachments)
                [:div
                 {:key sub-group}
                 [:div.dsp--flex.flex--between.flex--align-center.gap--b1.flex--wrap
                  [:h3.gap--b0 (att-grouping/group-loc sub-group)]
                  (when download?
                    [download-button sub-group-attachments])]
                 [group-display sub-group-attachments]]))])))]))

(defn attachment-state-group [{:keys [title-loc] :as options} attachment-ids]
  (when (not-empty attachment-ids)
    [:div.attachment-state-group
     [:h1 (str (loc title-loc) " (" (count attachment-ids) ")")]
     [attachment-group options attachment-ids]]))
