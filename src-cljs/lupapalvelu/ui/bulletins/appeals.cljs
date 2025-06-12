(ns lupapalvelu.ui.bulletins.appeals
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.attachment.components.upload-wrapper :as upload-wrapper]
            [lupapalvelu.ui.attachment.components.upload-zone :as upload-zone]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.util :as ui-util]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub
  ::pending?
  :-> ::pending?)

(rf/reg-sub
  ::submitted?
  (fn [db [_ bulletin-id]]
    (-> db ::submitted (contains? bulletin-id))))

(rf/reg-sub
  ::invalid-data?
  (fn [db]
    (let [{:keys [text email]} (::editing db)]
      (or (ss/blank? text)
          (ui-util/invalid-email? false email)))))

(rf/reg-event-db
  ::save-appeal.success
  (fn [db [_ _ bulletin-id files*]]
    (reset! files* [])
    (-> db
        (dissoc ::editing ::pending?)
        (update ::submitted (fnil conj #{}) bulletin-id))))

(rf/reg-event-fx
  ::save-appeal
  (fn [{:keys [db]} [_ bulletin-id files*]]
    {:db             (assoc db ::pending? true)
     :action/command {:name                  :add-bulletin-appeal
                      :params                (merge (::editing db)
                                                    {:bulletin-id bulletin-id
                                                     :files       (map (fn [{:keys [file-id]}]
                                                                         {:fileId file-id})
                                                                       @files*)})
                      :show-saved-indicator? true
                      :success               [::save-appeal.success bulletin-id files*]}}))

(defn- remove-file [files* {:keys [file-id]}]
  (common/command {:command :remove-uploaded-file
                   :success (fn []
                              (swap! files* (fn [files]
                                              (remove #(= file-id (:file-id %)) files))))}
                  :attachmentId file-id))

(defn attachment-table [files*]
  (let [pending? (<sub [::pending?])]
    [:div.pad--b2
     (if (seq @files*)
       [:table
        [:tbody
         (for [[idx {:keys [filename size state] :as file}] (util/indexed @files*)]
           ^{:key (str "appeal-file-" idx)}
           [:tr
            [:td filename]
            [:td (js/util.sizeString size)]
            [:td.td-center
             (components/icon-button {:class      :secondary.no-border
                                      :icon-only? true
                                      :on-click   #(remove-file files* file)
                                      :icon       :lupicon-remove
                                      :disabled?  pending?
                                      :wait?      (not= state :success)
                                      :test-id    [:file idx :remove]
                                      :text-loc   :remove})]])]]
       [:p (common/loc :bulletin.comment.noAttachments)])]))

(defn appeal-form [bulletin-id {:keys [first-name last-name street zip city]}]
  (r/with-let [files* (r/atom [])]
    (let [{:keys [text email]} (<sub [:value/get ::editing])
          invalid-data? (<sub [::invalid-data?])
          pending?      (<sub [::pending?])
          submitted?    (<sub [::submitted? bulletin-id])]
      (if submitted?
        [:div.container
         [:h5 (common/loc :bulletin.appeal.save.success)]]
        [:div.container.bulletin-comment {:id "appeal-form"}
         [:h5 (common/loc :bulletin.appeal.title)]
         [:div.form-grid.form-grid--no-border
          [:div.row
           [:span.strong (common/loc :bulletin.appeal.contact)]]
          [:div.row
           [:div.col-4.user-info
            [:div
             [:span (str first-name " " last-name)]]
            [:div street]
            [:div
             [:span (str zip " " city)]]]]

          [:div.row
           [:div.col-2
            [:label {:for "appeal-email"} (common/loc :email)]
            [components/text-edit email {:id       "appeal-email"
                                         :callback #(>evt [:value/set ::editing :email %])}]]]

          [:div.row
           [:div.col-4
            [:label.required {:for "appeal-text"} (common/loc :verdict.muutoksenhaku.rectification)]
            [components/textarea-edit text {:id       "appeal-text"
                                            :callback #(>evt [:value/set ::editing :text %])}]]]

          [:div.row
           [:div.col-2
            [attachment-table files*]
            (when (not pending?)
              [upload-wrapper/upload-wrapper {:callback  (upload/file-monitors files*)
                                              :dropzone  ".drop-zone-highlight"
                                              :multiple? true
                                              :component (fn [{:keys [input input-id]}]
                                                           (upload-zone/upload-zone input input-id))}])]]
          [:div.row
           (components/icon-button {:class     :primary.gap--r1
                                    :on-click  #(>evt [::save-appeal bulletin-id files*])
                                    :icon      :lupicon-comment
                                    :disabled? invalid-data?
                                    :wait?     pending?
                                    :text-loc  :bulletin.appeal.send})]]]))))
