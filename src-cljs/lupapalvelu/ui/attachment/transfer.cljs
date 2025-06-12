(ns lupapalvelu.ui.attachment.transfer
  "Transfer selected attachments either to backing system or case management."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.attachment.components.state-group :as att-group]
            [lupapalvelu.ui.attachment.filters :as att-filters]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.attachment.state-tags :as state-tags]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

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
      {:db               (update db ::transfer dissoc :selected :selected-bim :in-progress?)
       :fx               [[:dispatch [:indicator/positive :attachment.sent]]
                          [:dispatch [::fetch-bim-files]]]
       :application/load (common/application-id)
       :hub/send-n       {:events [[:attachmentsService::updateAll]
                                   [:external-api::attachments-sent]]}}
      {:db       (update db ::transfer dissoc :in-progress?)
       :dispatch [:indicator/negative (:text response)]})))

(rf/reg-event-fx ::move-attachments
  (fn [{db :db} [_ command ids]]
    {:db             (assoc-in db [::transfer :in-progress?] true)
     :action/command {:name      command
                      :params    {:id            (common/application-id)
                                  :attachmentIds ids
                                  :lang          (js/loc.getCurrentLanguage)}
                      :success   ::transfer-done
                      :error     ::transfer-done
                      :timed-out ::transfer-done}}))

(rf/reg-event-fx
  ::confirm-move-attachments
  (fn [_ [_ command ids]]
    {:dialog/show {:text-loc :transfer.already-sent-confirmation
                   :type     :yes-no
                   :event [::move-attachments command ids]}}))

(rf/reg-event-fx ::fetch-bim-files
  (fn [{_db :db} _]
    {:action/query {:name    :fetch-linked-file-metadatas-for-application
                    :params  {:id          (common/application-id)
                              :target-type "bim-model-file"}
                    :success [::fetch-bim-files-success]}}))

(rf/reg-event-db ::fetch-bim-files-success
  (fn [db [_ {:keys [data]}]]
    (assoc-in db [::transfer :bim-files] data)))

(rf/reg-event-db ::toggle-bim-selection
  (fn [db [_ checked? & file-ids]]
    (assoc-in db
              [::transfer :selected-bim]
              (apply (if checked? conj disj)
                     (set (get-in db [::transfer :selected-bim]))
                     (flatten file-ids)))))

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

(rf/reg-sub ::bim-files
  (fn [db] (get-in db [::transfer :bim-files])))

(defn- get-transferred-bim-ids
  "Returns a set of BIM file IDs that have been transferred to backing system."
  []
  (when-let [transfers (some-> js/lupapisteApp.models.application._js.transfers
                               (js->clj :keywordize-keys true)
                               seq)]
    (->> transfers
         (filter #(= (name (:type %)) "exported-to-backing-system"))
         (mapcat :attachments)
         set)))

(rf/reg-sub ::sendable-bim-files
  :<- [::bim-files]
  (fn [files]
    (->> files
         (filter #(seq (:versions %)))
         (sort-by (fn [file]
                    (->> (:versions file)
                         (apply util/max-key :uploaded-at)
                         :uploaded-at))))))

(rf/reg-sub ::filtered-bim-files
  (fn [[_ fltr-key]]
    [(rf/subscribe [::sendable-bim-files])
     (rf/subscribe [::att-filters/get-filters fltr-key :state :sent])])
  (fn [[files sent-filter] _]
    (let [transferred-ids (or (get-transferred-bim-ids) #{})]
      (cond
        ;; sent-filter = true: only show sent files
        (true? sent-filter)
        (filter #(transferred-ids (get-in % [:target-entity :file-id])) files)
        ;; sent-filter = false: exclude sent files
        (false? sent-filter)
        (remove #(transferred-ids (get-in % [:target-entity :file-id])) files)
        ;; sent-filter = nil: show all files
        :else
        files))))

(rf/reg-sub ::selected-bim
  (fn [db] (set (get-in db [::transfer :selected-bim]))))

(rf/reg-sub ::bim-files-for-ids
  :<- [::bim-files]
  (fn [files [_ ids]]
    (let [id-set (set ids)]
      (filter #(id-set (get-in % [:target-entity :file-id])) files))))

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

;; -------------------
;; BIM File Helpers
;; -------------------

(defn- bim-latest-version [{:keys [versions]}]
  (apply util/max-key :uploaded-at versions))

(defn- bim-filename [bim-file]
  (or (get-in (bim-latest-version bim-file) [:metadata :name])
      (get-in bim-file [:metadata :name])
      "Unknown"))

(defn- bim-file-link [{:keys [target-entity] :as bim-file}]
  [:a {:target "_blank"
       :href   (str "/api/raw/linked-file-version?file-id=" (:file-id target-entity))}
   (bim-filename bim-file)])

(defn- bim-model-type-text [{:keys [metadata]}]
  (when-let [model-type (:model-type metadata)]
    (path/loc :application.bim.model-type model-type)))

(defn- bim-sent?
  "Checks if a BIM file has been transferred by looking at the application's transfers."
  [file-id]
  (when-let [transfers (some-> js/lupapisteApp.models.application._js.transfers
                               (js->clj :keywordize-keys true)
                               seq)]
    (->> transfers
         (filter #(= (name (:type %)) "exported-to-backing-system"))
         (mapcat :attachments)
         (some #(= % file-id))
         boolean)))

;; -------------------
;; Attachment Helpers
;; -------------------

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
    [:table.collapsing.reset.w--100.attachment-table.gap--b2
     [:thead
     (into [:tr.bg--gray]
           (concat (for [column [:th-type :th-content :th-file :th-status :th-edited :version]]
                     [:th.txt--left.pad--1
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
           :let               [{:keys [user created version]} latestVersion
                               {:keys [firstName lastName]} user
                               {:keys [major minor]} version]]
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
        [:td.pad--1 (str major "." minor)]
        [:td.pad--1
         [components/toggle
          (contains? selected id)
          {:aria-label-loc :attachment.th-transfer
           :disabled?      in-progress?
           :callback       #(>evt [::toggle-selection % id])}]]])]]))

(defn- attachment-row
  "Renders a table row for a regular attachment in the selected summary."
  [{:keys [id contents latestVersion] :as attachment} in-progress?]
  (let [{:keys [created version]} latestVersion
        {:keys [major minor]} version]
    [:tr
     [:td.pad--1 (attachment-type attachment)]
     [:td.pad--1 contents]
     [:td.pad--1 (attachment-link attachment)]
     [:td.pad--1 [state-tags/state-tags attachment]]
     [:td.pad--1.ws--nowrap (date-time created)]
     [:td.pad--1 (str major "." minor)]
     [:td.pad--1
      [components/icon-button
       {:text-loc   :remove
        :class      :tertiary
        :disabled?  in-progress?
        :icon       :lupicon-remove
        :icon-only? true
        :on-click   #(>evt [::toggle-selection false id])}]]]))

(defn- bim-row
  "Renders a table row for a BIM file in the selected summary."
  [bim-file in-progress?]
  (let [file-id (get-in bim-file [:target-entity :file-id])
        latest  (bim-latest-version bim-file)
        {:keys [uploaded-at]} latest
        {:keys [major minor]} (:version latest)
        sent?   (bim-sent? file-id)]
    [:tr
     [:td.pad--1 (bim-model-type-text bim-file)]
     [:td.pad--1 (loc :transfer.bim-content)]
     [:td.pad--1 (bim-file-link bim-file)]
     [:td.pad--1
      (when sent?
        [:span.attachment-state-tag.bg--gray.pad--v1.pad--h2
         (common/loc :attachment.sent)])]
     [:td.pad--1.ws--nowrap (date-time uploaded-at)]
     [:td.pad--1 (str major "." minor)]
     [:td.pad--1
      [components/icon-button
       {:text-loc   :remove
        :class      :tertiary
        :disabled?  in-progress?
        :icon       :lupicon-remove
        :icon-only? true
        :on-click   #(>evt [::toggle-bim-selection false file-id])}]]]))

(defn combined-selected-summary []
  (let [att-ids      (seq (<sub [::selected]))
        bim-ids      (seq (<sub [::selected-bim]))
        attachments  (<sub [::shared/attachments-for-ids att-ids])
        bim-files    (<sub [::bim-files-for-ids bim-ids])
        in-progress? (<sub [::transfer-in-progress?])
        total-count  (+ (count att-ids) (count bim-ids))]
    (when (or att-ids bim-ids)
      [:div.gap--t4
       [:div.flex--between
        [:h1 (loc :transfer.selected total-count)]
        [:button.tertiary
         {:on-click #(do (when att-ids (>evt [::toggle-selection false att-ids]))
                         (when bim-ids (>evt [::toggle-bim-selection false bim-ids])))}
         (loc :pate.clear)]]
       [:table.collapsing.reset.w--100.attachment-table.gap--b2
        [:thead
         (into [:tr.bg--gray]
               (for [column [:th-type :th-content :th-file :th-status :th-edited :version :th-transfer]]
                 [:th.txt--left.pad--1
                  (path/loc :attachment column)]))]
        [:tbody
         (for [{:keys [id] :as attachment} attachments]
           ^{:key id}
           [attachment-row attachment in-progress?])
         (for [bim-file bim-files
               :let [file-id (get-in bim-file [:target-entity :file-id])]]
           ^{:key file-id}
           [bim-row bim-file in-progress?])]]])))

;; -------------------
;; BIM File Components
;; -------------------

(defn bim-file-table [bim-files]
  (let [selected      (<sub [::selected-bim])
        ids           (map #(get-in % [:target-entity :file-id]) bim-files)
        all-selected? (every? selected ids)
        in-progress?  (<sub [::transfer-in-progress?])]
    [:table.collapsing.reset.w--100.attachment-table.gap--b2
     [:thead
      (into [:tr.bg--gray]
            (concat (for [column [:th-type :th-content :th-file :th-status :th-edited :version]]
                      [:th.txt--left.pad--1
                       (path/loc :attachment column)])
                    [[:th.txt--left.pad--1.ws--nowrap
                      [components/toggle
                       all-selected?
                       {:text-loc  :attachment.th-transfer
                        :disabled? in-progress?
                        :callback  #(>evt [::toggle-bim-selection % ids])}]]]))]
     [:tbody
      (doall (for [bim-file bim-files
                   :let     [file-id       (get-in bim-file [:target-entity :file-id])
                             latest        (bim-latest-version bim-file)
                             {:keys [uploaded-at uploaded-by]} latest
                             {:keys [major minor]} (:version latest)
                             {:keys [firstName lastName]} uploaded-by
                             sent?         (bim-sent? file-id)]]
               ^{:key file-id}
               [:tr
                [:td.pad--1 (bim-model-type-text bim-file)]
                [:td.pad--1 (loc :transfer.bim-content)]
                [:td.pad--1 (bim-file-link bim-file)]
                [:td.pad--1
                 (when sent?
                   [:span.attachment-state-tag.bg--gray.pad--v1.pad--h2
                    (common/loc :attachment.sent)])]
                [:td.pad--1
                 [:span.ws--nowrap.gap--r1 (date-time uploaded-at)]
                 [:br.coll--hide]
                 (ss/join-non-blanks " " [firstName lastName])]
                [:td.pad--1 (str major "." minor)]
                [:td.pad--1
                 [components/toggle
                  (contains? selected file-id)
                  {:aria-label-loc :attachment.th-transfer
                   :disabled?      in-progress?
                   :callback       #(>evt [::toggle-bim-selection % file-id])}]]]))]]))

(defn transfer-button [text-loc command]
  (let [ids         (seq (<sub [::selected]))
        bim-ids     (seq (<sub [::selected-bim]))
        attachments (<sub [::shared/attachments-for-ids ids])]
    [components/icon-button {:text-loc  text-loc
                             :class     :primary
                             :wait?     (<sub [::transfer-in-progress?])
                             :icon      :lupicon-circle-arrow-right
                             :disabled? (and (empty? ids) (empty? bim-ids))
                             :on-click  #(>evt [(if (some :sent attachments)
                                                  ::confirm-move-attachments
                                                  ::move-attachments)
                                                command (concat ids bim-ids)])}]))

(defn transfer-view []
  (r/with-let [_ (>evt [::att-filters/configure-filters ::transfer
                        :prefilter :sendable-file
                        :groups [:has-file]
                        :filters {:state {:sent false}}])
               _ (attachments-changed)
               _ (>evt [:auth/refresh])
               _ (>evt [::hub/subscribe "attachmentsService::query" attachments-changed
                        ::query-event])
               _ (>evt [:application/sync])
               _ (>evt [::fetch-bim-files])]

    (let [attachments?        (not-empty (<sub [::att-filters/prefiltered ::transfer]))
          backing-system?     (<sub [:auth/application? :move-attachments-to-backing-system])
          cm?                 (<sub [:auth/application? :attachments-to-asianhallinta])
          [title-loc command] (cond backing-system?
                                    [:application.attachmentsMoveToBackingSystem
                                     :move-attachments-to-backing-system]

                                    cm?
                                    [:application.attachmentsMoveToCaseManagement
                                     :attachments-to-asianhallinta])
          files        (not-empty (:has-file (<sub [::att-filters/filtered-groups ::transfer])))
          selected     (not-empty (<sub [::selected]))
          bim-files    (not-empty (<sub [::filtered-bim-files ::transfer]))
          selected-bim (not-empty (<sub [::selected-bim]))]
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
             (or files bim-files)
             [:div.flex--column.flex--gap1
              (when files
                [att-group/attachment-state-group
                 {:filter-set-key ::transfer
                  :title-loc      :verdict.attachments
                  :group-display  attachment-table}
                 files])
              (when bim-files
                [:div.gap--t4
                 [:h2 (loc :application.data-models)]
                 [bim-file-table bim-files]])
              [combined-selected-summary]
              [:div [transfer-button title-loc command]]
              [:div.gap--t2 [back-button]]]

             (or selected selected-bim)
             [:div.flex--column.flex--gap1
              [combined-selected-summary]
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

(defonce mounter (ui-dom/init :reagent (constantly transfer-view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
