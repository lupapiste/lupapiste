(ns lupapalvelu.ui.docgen.field.docgen-fill-my-info-button
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.toggle :as toggle]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def tyonjohtaja-v2-document-name "tyonjohtaja-v2")

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::processing
  (fn [db _]
    (get-in db [::data :processing] false)))

(rf/reg-sub ::copy-attachments
  (fn [db _]
    (get-in db [::data :copy-attachments] true)))

(rf/reg-event-db ::set-copy-attachments
  (fn [db [_ value]]
    (assoc-in db [::data :copy-attachments] value)))

(rf/reg-event-db ::set-pending-operations
  (fn [db [_ count]]
    (assoc-in db [::data :pending-operations] count)))

(rf/reg-event-fx ::operation-completed
  (fn [{:keys [db]} [_ _ok opts]]
    (let [pending (dec (get-in db [::data :pending-operations] 0))]
      (cond-> {:db (assoc-in db [::data :pending-operations] pending)}
        (<= pending 0) (assoc :fx [[:dispatch [::fill-user-info.success opts]]])))))

(rf/reg-event-fx ::fill-user-info.success
  (fn [{:keys [db]} [_ {:keys [app-id doc-id]}]]
    {:db (assoc-in db [::data :processing] false)
     :fx [[:application/load app-id]
          [:hub/send {:event :documentChangedInBackendCljs
                      :data {:docId doc-id}}]]}))

(rf/reg-event-fx ::fill-user-info
  (fn [{:keys [db]} [_ {:keys [app-id doc-id doc-name path fill-attachments?]}]]
    (let [path-str        (->> path
                               butlast
                               (map name)
                               (ss/join ".")) ;; This is just an empty string always?
          operation-count (if fill-attachments? 2 1) ; Have to wait for both operations to finish to enable the button
          processing?     (get-in db [::data :processing])]
      (when-not processing? ;; if for some reason this event is triggered twice we don't want anything to happen
        {:db (-> db
                 (assoc-in [::data :processing] true)
                 (assoc-in [::data :pending-operations] operation-count))
         :fx [[:action/command {:name     :set-current-user-to-document
                                :params   {:id         app-id
                                           :documentId doc-id
                                           :path       path-str}
                                :complete [::operation-completed (util/mmap app-id doc-id)]}]
              (when fill-attachments?
                [:action/command {:name     :copy-user-attachments-to-application
                                  :params   {:id app-id}
                                  :complete [::operation-completed (util/mmap app-id doc-name)]}])]}))))

(rf/reg-sub ::show-attachments-option?
  (fn [[_ doc-id]]
    [(rf/subscribe [::docs/doc-name doc-id])
     (rf/subscribe [:auth/application? :copy-user-attachments-to-application])])
  (fn [[doc-name auth?]]
    (and (= tyonjohtaja-v2-document-name doc-name)
         auth?)))

(sc/defn render
  "Fill my info button component.
  Allows users to fill in their own information and optionally copy attachments."
  [{:keys [app-id doc-id path override-auth-source]} :- schemas/DocgenFieldArguments]
  (let [auth (<sub [::auth/auth? doc-id :set-current-user-to-document override-auth-source])]
    (when auth
      (let [doc-name (<sub [::docs/doc-name doc-id])
            show-attachments-option? (<sub [::show-attachments-option? doc-id])
            fill-attachments (<sub [::copy-attachments])
            disabled? (<sub [::docs/doc-disabled? doc-id nil])
            processing? (<sub [::processing])]
        [:div.fill-info-toolbar
         [:button.positive
          {:data-test-id "fill-info-button"
           :disabled (or disabled? processing?)
           :on-click #(>evt [::fill-user-info
                             {:app-id app-id
                              :doc-id doc-id
                              :doc-name doc-name
                              :path path
                              :fill-attachments? fill-attachments}])}
          [:i.lupicon-user-pen {:aria-hidden true}]

          [:span.pad--r05 (common/loc :fillInfoButton)]
          (when processing?
            [:i.lupicon-refresh.icon-spin.text-sm
             {:title (common/loc :operations-locations.check-model-pending)}])]
         (when show-attachments-option?
           [toggle/toggle
            {:checked? fill-attachments
             :ltext :application.attachmentsCopyOwn
             :prefix "buttonbox"
             :disabled? (or disabled? processing?)
             :on-change #(>evt [::set-copy-attachments %])}])]))))
