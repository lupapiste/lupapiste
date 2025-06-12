(ns lupapalvelu.ui.docgen.field.huoneistot.premise-upload
  "Implement uploading premise data as an Excel file."
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.field.huoneistot.highlight :as highlight]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn unload-data
  [db]
  (dissoc db ::data))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::show-premises-upload?
  (fn show-premises-upload-inputfn [[_ doc-id allow-premise-upload? allow-highlight-changes?]]
    {:pre [(string? doc-id)
           (boolean? allow-premise-upload?)
           (boolean? allow-highlight-changes?)]}
    [(rf/subscribe [:auth/application? :download-premises-template])
     (rf/subscribe [::highlight/highlight-changes? doc-id allow-highlight-changes?])])
  (fn [[auth? highlight-changes?] [_ _doc-id allow-premise-upload? _]]
    (and auth?
         (not highlight-changes?)
         allow-premise-upload?)))

(rf/reg-event-fx ::upload-premises-data.success
  (fn [_ [_ _resp app-id]]
    {:fx [[:application/load app-id]]}))

(rf/reg-event-fx ::upload-premises-data.complete
  (fn [{:keys [db]} [_ _resp doc-id]]
    {:db (assoc-in db [::data :pending? doc-id] false)}))

(rf/reg-event-fx ::upload-file-selected
  (fn [{:keys [db]} [_ {:keys [app-id doc-id file]}]]
    {:db        (assoc-in db [::data :pending? doc-id] true)
     :post/form {:raw-name :upload-premises-data
                 :params   {:id       app-id
                            :doc      doc-id
                            "files[]" file}
                 :success  [::upload-premises-data.success app-id]
                 :complete [::upload-premises-data.complete doc-id]}}))

(rf/reg-sub ::upload-pending-flags
  :<- [::data]
  :-> :pending?)

(rf/reg-sub ::upload-pending?
  :<- [::upload-pending-flags]
  (fn [flags [_ doc-id]]
    (boolean (get flags doc-id))))

(defn button
  "Button for uploading an Excel document containing the premises data."
  [{:keys [app-id disabled? doc-id allow-premise-upload? allow-highlight-changes?]}]
  (when (<sub [::show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?])
    (let [;; Keep track of the file input DOM element so we can later "click it"
          file-input-ref (react/useRef)]
      [:span
       [:form.upload-component.premises-upload
        [:input.hidden
         {:type      "file"
          :accept    ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                      "application/vnd.ms-excel"]
          :ref       file-input-ref
          :disabled  disabled?
          :on-change (fn [e]
                       (let [file (-> e .-target .-files (.item 0))]
                         (>evt [::upload-file-selected (util/mmap app-id doc-id file)])))}]
        [:button.btn.secondary
         {:class    [(when (<sub [::upload-pending? doc-id])
                       "waiting")]
          :on-click (fn [e]
                      ;; Pretend the user clicked on the file input
                      ;; => browser shows the file picker dialog
                      (.click (.-current file-input-ref))
                      (.preventDefault e))}
         [:i.lupicon-upload]
         [:i.wait.spin.lupicon-refresh]
         [:span (common/loc :huoneistot.premisesUploadButton)]]]])))

(rf/reg-sub ::premises-upload-url
  :<- [::app/application-id]
  (fn [app-id [_ doc-id]]
    (common/make-url "/api/raw/download-premises-template"
                     {:application-id  app-id
                      :document-id     doc-id
                      ;; Ideally we ought to have a subscription for the language
                      :lang            (common/get-current-language)})))

(defn note
  "Help text regarding the upload."
  [{:keys [doc-id allow-premise-upload? allow-highlight-changes?]}]
  (when (<sub [::show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?])
    [:<>
     [:br]
     [:span.group-help-text
      [:span (common/loc :huoneistot.premisesInfoText)]
      " "
      [:a {:href (<sub [::premises-upload-url doc-id])}
       (common/loc :huoneistot.premisesDownloadLink)]]]))
