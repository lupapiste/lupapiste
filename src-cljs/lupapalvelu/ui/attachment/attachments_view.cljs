(ns lupapalvelu.ui.attachment.attachments-view
  (:require
   [lupapalvelu.common.hub :as hub]
   [lupapalvelu.next.event :refer [>evt <sub]]
   [lupapalvelu.pate.path :as path]
   [lupapalvelu.ui.attachment.approval :as appro]
   [lupapalvelu.ui.attachment.assignments :as att-assignments]
   [lupapalvelu.ui.attachment.components :as att]
   [lupapalvelu.ui.attachment.filters :as att-filters]
   [lupapalvelu.ui.attachment.shared :as shared]
   [lupapalvelu.ui.attachment.state-tags :as state-tags]
   [lupapalvelu.ui.common :refer [loc] :as common]
   [lupapalvelu.ui.components :as components]
   [reagent.core :as r]
   [reagent.dom :as rd]
   [sade.shared-strings :as ss]
   [sade.shared-util :as util]))

(def attachment-service js/lupapisteApp.services.attachmentsService)
(def *upload (atom nil))

(defn- attachments-changed []
  (>evt [::att-filters/refresh-operations])
  (>evt [::shared/refresh-attachments])
  (>evt [::att-filters/check-filters ::attachments]))

(defn- delete-event []
  (attachments-changed)
  (>evt [::att-filters/filter-and-group ::attachments]))

(defn- bind-event [{:keys [status]}]
  (when (= status "done")
    (>evt [::att-filters/filter-and-group ::attachments])))


(defn type-loc [& args]
  (path/loc :attachmentType args))

(defn version-info [{:keys [user created]}]
  (when created
    [:<>
     [:span.ws--nowrap.gap--r1 (js/util.finnishDateAndTime created "D.M.YYYY / HH.mm")]
     [:br.coll--hide]
     (:firstName user) " " (:lastName user)]))

(defn remark-counter [remarks]
  [:span.attachment-remark-counter
   {:aria-hidden true
    :class       (if (pos? remarks)
                   :attachment-remark-warn
                   :attachment-remark-ok)}
   remarks])

(defn visibility [{:keys [visibility]}]
  [:div.attachment-visibility
   [:h5 (loc "attachment.th-visibility")]
   (when-not (= visibility "julkinen")
     [:i.lupicon-lock {:aria-hidden true}])
   [:div (path/loc :attachment.visibility visibility)]])

(defn signatories [signatures]
  [:div.w--100
   [:h5 (loc :attachment.signatures)]
   (ss/join ", " signatures)])

(defn archival-error [error-loc]
  [:div.like-btn
   [:i.lupicon-circle-info.negative {:aria-hidden true}]
   [:span (loc error-loc)]])

(defn detail-sub-rows [attachment column-count]
  (let [[edit-target & edit-args
         :as editing]          (<sub [::shared/editing attachment])
        note                   (appro/reject-note attachment)
        assignments            (<sub [::shared/attachment-assignments attachment])
        can-create-assignment? (<sub  [:auth/application? :create-assignment])
        show-assignments?      (or (-> assignments count pos?) can-create-assignment?)

        col-span      (dec column-count)
        required-note (and (<sub [:auth/attachment? (:id attachment)
                                  :set-attachment-not-needed])
                           (not (or (:latestVersion attachment)
                                    (:notNeeded attachment))))
        signatures    (<sub [::shared/latest-version-signatures attachment])
        arch-error    (<sub [::shared/archival-error attachment])]
    [:<>
     (when (or editing note show-assignments? required-note signatures)
       [:tr.expanded-2
        [:td.coll--hide]
        [:td.pad--1.coll--100 {:col-span col-span}
         [:div.bd--v-gray.dsp--flex
          {:class (common/css-flags :flex--right (= edit-target :reject-note)
                                    :flex--column required-note)}
          (case edit-target
            :reject-note [appro/reject-note-editor attachment]

            :new-assignment  [att-assignments/assignment-editor attachment]
            :edit-assignment [att-assignments/assignment-editor attachment (first edit-args)]

            [:<>
             (when required-note
               [:div.pate-note (loc :a11y.attachment.required-note)])
             (when (or show-assignments? note)
               [:div.flex--between.w--100.flex--wrap-s
                (when show-assignments?
                  [att-assignments/assignments-sub-row assignments attachment])
                (when note
                  [appro/reject-note-details attachment])])])
          ]
         (when signatures
           [signatories signatures])
         (when arch-error
           [archival-error arch-error])]])
     [:tr.expanded-3
      [:td.coll--hide]
      [:td.pad--1.coll--100
       {:col-span col-span}
       [:div.flex--between.flex--wrap-xs
        [visibility attachment]
        (when (<sub [:auth/attachment? (:id attachment) :delete-attachment])
          [components/icon-button {:class     [:tertiary :btn-small]
                                   :on-click  #(att/delete-with-confirmation attachment)
                                   :icon      :lupicon-trash
                                   :right?    true
                                   :disabled? editing
                                   :text-loc  "attachment.delete"}])]]]]))

(defn upload-button [{:keys [id group type]}]
  (when (<sub [:auth/application? :upload-file-authenticated])
    [:div (att/upload-button
            (fn [evt]
              (let [file           (-> evt
                                       (js->clj :keywordize-keys true)
                                       :files
                                      first)
                    file-with-meta (assoc file :type type
                                          :group group
                                          :attachmentId id)]
                (.push (.-files @*upload) (clj->js file-with-meta)))))]))

(defn file-link [{:keys [id latestVersion] :as attachment}]
  (if latestVersion
    [:div
     [:a {:target "_blank"
          :href   (str "/api/raw/latest-attachment-version?attachment-id=" id)}
      (:filename latestVersion)]]
    [upload-button attachment]))

(defn not-needed [{:keys [notNeeded id]}]
  (r/with-let [not-needed* (r/atom notNeeded)]
    (let [auth? (<sub [:auth/attachment? id :set-attachment-not-needed])]
      (when (or notNeeded auth?)
        [components/toggle
         @not-needed*
         {:text-loc :application.attachmentNotNeeded
          :callback (fn [not-needed]
                      (reset! not-needed* not-needed)
                      (.setNotNeeded attachment-service id not-needed
                                     {:field "not-needed"}))
          :enabled? auth?}]))))

(defn attachment-row [{:keys [id type contents latestVersion notNeeded]
                       :as   attachment}
                      can-approve-reject?
                      column-count]
  (let [assignments (count (<sub [::shared/attachment-assignments attachment]))
        remarks     (cond-> assignments
                      (appro/reject-note attachment)              inc
                      (not (or latestVersion notNeeded))          inc
                      (<sub [::shared/archival-error attachment]) inc)
        editing     (<sub [::shared/editing id])
        expanded?   (or (<sub [::shared/expanded? id]) editing)]
    [:<>
     [:tr {:class (common/css-flags :expanded expanded?)}
      [:td.coll--last.pad--1
       [:button.tertiary
        {:aria-label    (loc :a11y.attachment.notices remarks)
         :aria-expanded expanded?
         :disabled      editing
         :on-click      #(>evt [::shared/set-expanded id (not expanded?)])}
        [:i {:class       (if expanded? "lupicon-chevron-up" "lupicon-chevron-down")
             :aria-hidden true}]
        [remark-counter remarks]]]
      [:td.pad--1
       [:a
        {:href (js/pageutil.buildPageHash "attachment" (common/application-id) id)}
        (type-loc (:type-group type) (:type-id type))]]
      [:td.pad--1 contents]
      [:td.pad--1 (when-not notNeeded [file-link attachment])]
      (if latestVersion
        [:<>
         [:td.pad--1 [state-tags/state-tags attachment]]
         [:td.pad--1 [version-info latestVersion]]]
        [:td.pad--1 {:col-span 2}
         [not-needed attachment]])
      (when can-approve-reject?
        [:td.coll--100.pad--1 [appro/approval-buttons attachment]])]
     (when expanded?
       [detail-sub-rows attachment column-count])]))

(defn attachment-table [attachments]
  (let [can-approve-reject? (<sub [:auth/any-attachment? attachments :approve-attachment])
        columns             (cond-> [:th-notice :th-type :th-content
                                     :th-file :th-status :th-edited]
                              can-approve-reject?
                              (conj :th-remarks))]
    [:table.collapse.reset.w--100.attachment-table.gap--b2
     [:thead
      [:tr.bg--gray
       (for [column columns]
         [:th.txt--left.pad--1
          {:key column}
          (loc (util/kw-path :attachment column))])]]
     [:tbody
      (for [attachment attachments]
        ^{:key (:id attachment)} [attachment-row attachment can-approve-reject?
                                  (count columns)])]]))

(defn state-group [title-loc attachment-ids]
  [att/attachment-state-group
   {:filter-set-key ::attachments
    :title-loc      title-loc
    :group-display  attachment-table
    :download?      true}
   attachment-ids])

(defn attachment-view []
  (r/with-let [_ (>evt [::att-filters/configure-filters ::attachments
                        :groups [:incomplete :updated :ok :not-needed]])
               _ (attachments-changed)
               _ (>evt [:auth/refresh])
               _ (>evt [::hub/subscribe "attachmentsService::query" attachments-changed
                        ::query-event])
               _ (>evt [::hub/subscribe "attachmentsService::update" attachments-changed
                        ::update-event])
               _ (>evt [::hub/subscribe "attachmentsService::remove" delete-event
                        ::delete-event])
               _ (>evt [::hub/subscribe "attachmentsService::bind-attachments-status" bind-event
                        ::bind-event])
               _ (>evt [::hub/subscribe att-assignments/assignment-change-sub
                        att-assignments/assignments-changed
                        ::assignments-changed])
               _ (>evt [::shared/refresh-assignments])]
    (let [attachments?   (not-empty (<sub [::att-filters/prefiltered]))
          {:keys [incomplete updated ok not-needed]
           :as   groups} (not-empty (<sub [::att-filters/filtered-groups ::attachments]))]
      [:div
       (when attachments?
         [att-filters/filter-settings ::attachments])
       (if groups
         [:<>
          [state-group "attachment.state.incomplete" incomplete]
          [state-group "attachment.state.updated" updated]
          [state-group "attachment.state.ok" ok]
          [state-group "attachment.state.not-needed" not-needed]]
         [:div.attachment-no-results
          [:i.lupicon-circle-attention.negative {:aria-hidden true}]
          [:span (loc (if attachments?
                        "attachment.listing.no-filtered-attachments"
                        "attachment.listing.no-attachments"))]])])
    (finally
      (>evt [::hub/unsubscribe ::query-event ::update-event ::delete-event
             ::assignments-changed ::bind-event]))))

(defn mount-component [dom-id]
  (rd/render [attachment-view] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (reset! *upload (common/oget params "upload"))
  (mount-component dom-id))
