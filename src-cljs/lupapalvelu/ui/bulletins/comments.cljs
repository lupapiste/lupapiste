(ns lupapalvelu.ui.bulletins.comments
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.attachment.components.upload-wrapper :as upload-wrapper]
            [lupapalvelu.ui.attachment.components.upload-zone :as upload-zone]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.util :as ui-util]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-event-db
  ::set-params
  (fn [db [_ params]]
    (assoc db ::params params)))

(rf/reg-event-fx
  ::save-comment.success
  (fn [{:keys [db]} [_ response files*]]
    (if (:ok response)
      (do
        (reset! files* [])
        {:db         (dissoc db ::editing)
         :dispatch   [:indicator/positive :saved]
         :hub/send-n {:events [[:publishBulletinService::fetchBulletinVersions {:bulletinId (get-in db [::params :app-id])}]]}})
      {:dispatch [:indicator/negative (:text response)]})))

(rf/reg-event-fx
  ::save-comment
  (fn [{:keys [db]} [_ files*]]
    (let [params (::params db)]
      {:action/command {:name    :add-manual-bulletin-comment
                        :params  (merge (::editing db)
                                        {:id                (:app-id params)
                                         :bulletinId        (:app-id params)
                                         :bulletinVersionId (->> params :bulletin :versions last :id)
                                         :files             (map (fn [{:keys [file-id filename type size]}]
                                                                   {:fileId      file-id
                                                                    :filename    filename
                                                                    :contentType type
                                                                    :size        size})
                                                                 @files*)})
                        :success [::save-comment.success files*]}})))

(rf/reg-sub
  ::invalid-data?
  (fn [db]
    (let [{{:keys [firstName lastName street zip city email emailPreferred]} :contact comment :comment} (::editing db)]
      (or (ss/blank? firstName)
          (ss/blank? lastName)
          (ss/blank? street)
          (ss/blank? zip)
          (ss/blank? city)
          (ss/blank? comment)
          (when emailPreferred (ui-util/invalid-email? true email))))))

(defn- remove-file [files* {:keys [file-id]}]
  (common/command {:command :remove-uploaded-file
                   :success (fn []
                              (swap! files* (fn [files]
                                              (remove #(= file-id (:file-id %)) files))))}
                  :attachmentId file-id))

(defn attachment-table [files*]
  [:div.pad--b2
   (if (seq @files*)
     [:table
      [:tbody
       (for [[idx {:keys [filename size state] :as file}] (util/indexed @files*)]
         ^{:key (str "comment-file-" idx)}
         [:tr
          [:td filename]
          [:td (js/util.sizeString size)]
          [:td.td-center
           (components/icon-button {:class      :secondary.no-border
                                    :icon-only? true
                                    :on-click   #(remove-file files* file)
                                    :icon       :lupicon-remove
                                    :wait?      (not= state :success)
                                    :test-id    [:file idx :remove]
                                    :text-loc   :remove})]])]]
     [:p (common/loc :bulletin.comment.noAttachments)])])

(defn comment-form []
  (r/with-let [files* (r/atom [])]
    (let [{{:keys [firstName lastName street zip city email emailPreferred]} :contact comment :comment} (<sub [:value/get ::editing])
          submit-disabled?                                                                              (<sub [::invalid-data?])]
      [:form#bulletin-comment-form.form-grid.form-grid--no-border
       [:div.row
        [:label.txt--bold (common/loc :bulletin.comment.delivery)]]
       [:div.row
        [:div.col-4
         [:div.form-grid.form-grid--no-border
          [:div.row
           [:div.col-1
            [:label.required {:for "comment-first-name"} (common/loc :userinfo.firstName)]
            [components/text-edit firstName {:id        "comment-first-name"
                                             :callback  #(>evt [:value/set ::editing :contact :firstName %])
                                             :required? true}]]
           [:div.col-1
            [:label.required {:id "comment-last-name"} (common/loc :userinfo.lastName)]
            [components/text-edit lastName {:id        "comment-last-name"
                                            :callback  #(>evt [:value/set ::editing :contact :lastName %])
                                            :required? true}]]]
          [:div.row
           [:div.col-1
            [:label.required {:id "comment-street"} (common/loc :userinfo.street)]
            [components/text-edit street {:id        "comment-street"
                                          :callback  #(>evt [:value/set ::editing :contact :street %])
                                          :required? true}]]
           [:div.col-1
            [:label.required {:id "comment-zip"} (common/loc :userinfo.zip)]
            [components/text-edit zip {:id        "comment-zip"
                                       :callback  #(>evt [:value/set ::editing :contact :zip %])
                                       :required? true}]]
           [:div.col-1
            [:label.required {:id "comment-city"} (common/loc :userinfo.city)]
            [components/text-edit city {:id        "comment-city"
                                        :callback  #(>evt [:value/set ::editing :contact :city %])
                                        :required? true}]]]
          [:div.row
           [:div.col-1
            [:label {:id "comment-email"} (common/loc :userinfo.email)]
            [components/text-edit email {:id        "comment-email"
                                         :callback  #(>evt [:value/set ::editing :contact :email %])
                                         :required? true
                                         :invalid?  (and emailPreferred (ui-util/invalid-email? true email))}]]
           [:div.col-1.gap--v2
            [components/toggle emailPreferred {:text-loc  :bulletin.comment.emailPreferred.manual
                                               :callback  #(>evt [:value/set ::editing :contact :emailPreferred %])
                                               :disabled? (ss/blank? email)}]]]]]]
       [:div.row
        [:div.col-4
         [:label (common/loc :bulletin.comment)]
         [components/textarea-edit comment {:callback #(>evt [:value/set ::editing :comment %])}]]]
       [:div.row
        [:label.txt--bold (common/loc :bulletin.comment.selectedAttachments)]]
       [:div.row
        [:div.col-2
         [attachment-table files*]
         [upload-wrapper/upload-wrapper {:callback  (upload/file-monitors files*)
                                         :dropzone  ".drop-zone-highlight"
                                         :multiple? true
                                         :component (fn [{:keys [input input-id]}]
                                                      (upload-zone/upload-zone input input-id))}]]]

       [:div.row
        (components/icon-button {:class     :primary.gap--r1
                                 :on-click  #(>evt [::save-comment files*])
                                 :icon      :lupicon-save
                                 :disabled? submit-disabled?
                                 :text-loc  :save})
        (components/icon-button {:class    :secondary
                                 :on-click (fn []
                                             (>evt [:value/set ::editing nil])
                                             (doseq [file @files*]
                                               (remove-file files* file)))
                                 :icon     :lupicon-remove
                                 :text-loc :cancel})]])))

(defn comment-editor []
  (let [data (<sub [:value/get ::editing])]
    (if data
      [comment-form]
      [:div [:button.primary
             {:on-click #(>evt [:value/set ::editing {}])}
             (common/loc :bulletin.comment.add)]])))

(defonce mounter (ui-dom/init :reagent (constantly comment-editor)))

(defn ^:export start [dom-id params]
  (>evt [::set-params {:app-id   (common/->cljs (common/oget params "appId"))
                       :bulletin (common/->cljs (common/oget params "bulletin"))}])
  (ui-dom/mount! mounter dom-id))
