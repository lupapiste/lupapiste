(ns lupapalvelu.ui.attachment.transfer
  "Transfer selected attachments either to backing system or case management."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.attachment.components :as att-components]
            [lupapalvelu.ui.attachment.filters :as att-filters]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.attachment.state-tags :as state-tags]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn- id-set [db]
  (set (get-in db [::transfer :selected])))

;; -------------------
;; Events
;; -------------------

(rf/reg-event-fx
  ::reset-transfer
  (fn [{db :db} _]
    {:db       (dissoc db ::transfer)
     :dispatch [::att-filters/clear-filters ::transfer]}))

(rf/reg-event-db
  ::toggle-selection
  (fn [db [_ checked? & ids]]
    (assoc-in db
              [::transfer :selected]
              (apply (if checked? conj disj)
                     (id-set db)
                     (flatten ids)))))

(rf/reg-event-fx
  ::transfer-done
  (fn [{db :db} [_ response]]
    (if (:ok response)
      {:db         (update db ::transfer dissoc :selected :in-progress?)
       :dispatch   [:indicator/positive :attachment.sent]
       :hub/send-n {:events [[:attachmentsService::updateAll]
                             [:external-api::attachments-sent]]}}
      {:db       (update db ::transfer dissoc :in-progress?)
       :dispatch [:indicator/negative  (:text response)]})))

(rf/reg-event-fx
  ::move-attachments
  (fn [{db :db} [_ command ids]]
    {:db             (assoc-in db [::transfer :in-progress?] true)
     :action/command {:name       command
                      :params     {:id            (common/application-id)
                                   :attachmentIds ids
                                   :lang          (js/loc.getCurrentLanguage)}
                      :success    ::transfer-done
                      :error      ::transfer-done
                      :on-timeout ::transfer-done}}))

(rf/reg-event-fx
  ::confirm-move-attachments
  (fn [_ [_ command ids]]
    {:dialog/show {:text-loc :transfer.already-sent-confirmation
                   :type     :yes-no
                   :event [::move-attachments command ids]}}))

;; -------------------
;; Subs
;; -------------------

(rf/reg-sub
  ::selected
  (fn [db]
    (id-set db)))

(rf/reg-sub
  ::transfer-in-progress?
  (fn [db]
    (some-> db ::transfer :in-progress?)))

;; -------------------
;; Components
;; -------------------

(defn- back []
  (>evt [::reset-transfer])
  (common/open-page (str "application/" (common/application-id)) :attachments))

(defn- attachments-changed []
  (>evt [::att-filters/refresh-operations])
  (>evt [::shared/refresh-attachments])
  (>evt [::att-filters/check-filters ::transfer]))

(defn back-button []
  [components/icon-button {:text-loc  :application.return
                           :on-click  back
                           :disabled? (<sub [::transfer-in-progress?])
                           :class     :secondary
                           :icon      :lupicon-chevron-start}])

(defn- attachment-type [{type :type}]
  (path/loc :attachmentType (:type-group type) (:type-id type)))

(defn- attachment-link [{:keys [id latestVersion]}]
  [:a {:target "_blank"
       :href   (str "/api/raw/latest-attachment-version?attachment-id=" id)}
   (:filename latestVersion)])

(defn- date-time [ts]
  (some-> ts (js/util.finnishDateAndTime "DD.M.YYYY / HH.mm")))

(defn attachment-table [attachments]
  (let [selected      (<sub [::selected])
        ids           (map :id attachments)
        all-selected? (every? selected ids)
        in-progress?  (<sub [::transfer-in-progress?])]
    [:table.collapse.reset.w--100.attachment-table.gap--b2
     [:thead
     (into [:tr.bg--gray]
           (concat (for [column [:th-type :th-content :th-file :th-status :th-edited]]
                     [:th.txt--left.pad--1.w--20
                      (path/loc :attachment column)])
                   [[:th.txt--left.pad--1.ws--nowrap
                     [components/toggle
                      all-selected?
                      {:text-loc  :attachment.th-transfer
                       :disabled? in-progress?
                       :callback  #(>evt [::toggle-selection % ids])}]]]))]
    [:tbody
     (for [{:keys [id contents latestVersion]
            :as   attachment} attachments
           :let               [{:keys [user created]} latestVersion
                               {:keys [firstName lastName]} user]]
       ^{:key id}
       [:tr
        [:td.pad--1 (attachment-type attachment)]
        [:td.pad--1 contents]
        [:td.pad--1 (attachment-link attachment)]
        [:td.pad--1 [state-tags/state-tags attachment]]
        [:td.pad--1
         [:span.ws--nowrap.gap--r1 (date-time created)]
         [:br.coll--hide]
         (ss/join-non-blanks " " [firstName lastName])]
        [:td.pad--1
         [components/toggle
          (contains? selected id)
          {:aria-label-loc :attachment.th-transfer
           :disabled?      in-progress?
           :callback       #(>evt [::toggle-selection % id])}]]])]]))

(defn selected-summary []
  (let [ids          (seq (<sub [::selected]))
        attachments  (<sub [::shared/attachments-for-ids ids])
        in-progress? (<sub [::transfer-in-progress?])]

    (when ids
      [:div.gap--t4
       [:div.flex--between
        [:h1 (loc :transfer.selected (count ids))]
        [:button.tertiary
         {:on-click #(>evt [::toggle-selection false ids])}
         (loc :pate.clear)]]
       [:table.collapse.reset.w--100.attachment-table.gap--b2
        [:thead
         (into [:tr.bg--gray]
               (for [column [:th-type :th-file :th-edited :sent :th-transfer]]
                 [(cond-> :th.txt--left.pad--1
                    (not= column :th-transfer) (util/kw-path :w--25))
                  (path/loc :attachment column)]))]
        [:tbody
         (for [{:keys [id latestVersion sent]
                :as   attachment} attachments]
           ^{:key id}
           [:tr
            [:td.pad--1 (attachment-type attachment)]
            [:td.pad--1 (attachment-link attachment)]
            [:td.pad--1.ws--nowrap
             (date-time (:created latestVersion))]
            [:td.pad--1.ws--nowrap
             (date-time sent)]
            [:td.pad--1
             [components/icon-button
              {:text-loc   :remove
               :class      :tertiary
               :disabled?  in-progress?
               :icon       :lupicon-remove
               :icon-only? true
               :on-click   #(>evt [::toggle-selection false id])}]]])]]])))

(defn transfer-button [text-loc command]
  (let [ids         (seq (<sub [::selected]))
        attachments (<sub [::shared/attachments-for-ids ids])]
    [components/icon-button {:text-loc  text-loc
                             :class     :primary
                             :wait?     (<sub [::transfer-in-progress?])
                             :icon      :lupicon-circle-arrow-right
                             :disabled? (empty? ids)
                             :on-click  #(>evt [(if (some :sent attachments)
                                                  ::confirm-move-attachments
                                                  ::move-attachments)
                                                command ids])}]))

(defn transfer-view []
  (r/with-let [_ (>evt [::att-filters/configure-filters ::transfer
                        :prefilter :sendable-file
                        :groups [:has-file]
                        :filters {:state {:sent false}}])
               _ (attachments-changed)
               _ (>evt [:auth/refresh])
               _ (>evt [::hub/subscribe "attachmentsService::query" attachments-changed
                        ::query-event])
               _ (>evt [:application/sync])]

    (let [attachments?        (not-empty (<sub [::att-filters/prefiltered ::transfer]))
          backing-system?     (<sub [:auth/application? :move-attachments-to-backing-system])
          cm?                 (<sub [:auth/application? :attachments-to-asianhallinta])
          [title-loc command] (cond backing-system?
                                    [:application.attachmentsMoveToBackingSystem
                                     :move-attachments-to-backing-system]

                                    cm?
                                    [:application.attachmentsMoveToCaseManagement
                                     :attachments-to-asianhallinta])
          files    (not-empty (:has-file (<sub [::att-filters/filtered-groups ::transfer])))
          selected (not-empty (<sub [::selected]))]
      [:div.pad--1.flex--column.flex--row-gap2
       [:div [back-button]]
       (when command
         [:<>
          [:h1 (loc title-loc)]
          [:div
           (when attachments?
             [att-filters/filter-settings ::transfer :contents :operation
              :phase :category :type :state])
           (cond
             files
             [:div.flex--column.flex--gap1
              [att-components/attachment-state-group
               {:filter-set-key ::transfer
                :title-loc      :verdict.attachments
                :group-display  attachment-table}
               files]
              [selected-summary]
              [:div [transfer-button title-loc command]]
              [:div.gap--t2 [back-button]]]

             selected
             [:div.flex--column.flex--gap1
              [selected-summary]
              [:div [transfer-button title-loc command]]
              [:div.gap--t2 [back-button]]]

             :else
             [:div.attachment-no-results
              [:i.lupicon-circle-attention.negative {:aria-hidden true}]
              [:span (loc (if attachments?
                            "attachment.listing.no-filtered-attachments"
                            "attachment.listing.no-attachments"))]])]])])
    (finally
      (>evt [::hub/unsubscribe ::query-event]))))

(defn mount-component [dom-id]
  (rd/render [transfer-view] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id]
  (mount-component dom-id))
