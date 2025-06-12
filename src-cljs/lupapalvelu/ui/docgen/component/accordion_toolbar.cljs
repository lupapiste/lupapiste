(ns lupapalvelu.ui.docgen.component.accordion-toolbar
  "The accordion toolbar provides tools for manipulating the document within the accordion."
  (:require [goog.string :as goos]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.accordion-assignments :as accordion-assignments]
            [lupapalvelu.ui.docgen.component.approval-common :as approval-common]
            [lupapalvelu.ui.docgen.component.building-identifier :as identifier-building]
            [lupapalvelu.ui.docgen.component.document-building-reference :as document-building-reference]
            [lupapalvelu.ui.docgen.component.document-identifier :as identifier-doc]
            [lupapalvelu.ui.docgen.component.operation-editor :as operation-editor]
            [lupapalvelu.ui.docgen.component.reject-note :as reject-note]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.assignments :as assignments]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.edit :as edit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.identifiers :as identifiers]
            [lupapalvelu.ui.docgen.feature.operation :as operation]
            [lupapalvelu.ui.docgen.feature.select-options :as select-options]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.misc] ; :auth/*
            [lupapalvelu.ui.user :as user]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

;;; Subs

(rf/reg-sub ::may-reject?
  (fn may-reject-inputfn [[_ doc-id _override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::docs/approvable? doc-id]))
  (fn [approvable? [_ doc-id override-auth-source]]
    (approval-common/may-reject? (util/mmap doc-id override-auth-source approvable?))))

(rf/reg-sub ::may-approve-or-reason
  (fn may-approve-inputfn [[_ doc-id _override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::docs/approvable? doc-id]))
  (fn [approvable? [_ doc-id override-auth-source]]
    (approval-common/may-approve? (util/mmap doc-id override-auth-source approvable?))))

(rf/reg-sub ::use-designer-approval
  (fn [[_ doc-id]]
    (rf/subscribe [::docs/schema-info doc-id]))
  (fn [schema-info _]
    (:use-designer-approval schema-info)))

(rf/reg-sub ::may-remove?
  (fn may-remove-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    [(rf/subscribe [::docs/primary-operation? doc-id])
     (rf/subscribe [::auth/auth? doc-id :remove-doc override-auth-source])])
  (fn [[primary? ok?]]
    (and (not primary?) ok?)))

(rf/reg-sub ::may-replace?
  (fn may-replace-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::docs/operation doc-id])
     (rf/subscribe [::auth/auth? doc-id :replace-operation :auth/application?])])
  (fn [[op ok?]]
    (and (some? (:id op)) ok?)))

(rf/reg-sub ::may-replace-catalog-operation?
  (fn may-replace-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :update-building-product-catalog-operation override-auth-source]))
  :-> identity)

(rf/reg-sub ::may-toggle-edit?
  (fn may-toggle-edit-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    [(rf/subscribe [::docs/post-verdict-editable? doc-id])
     (rf/subscribe [::auth/auth? doc-id :update-post-verdict-doc override-auth-source])])
  (fn [[editable? ok?]]
    (and editable? ok?)))

(rf/reg-sub ::may-send-doc-updates?
  (fn may-send-doc-updates-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :send-doc-updates override-auth-source]))
  :-> identity)

(rf/reg-sub ::may-disable?
  (fn may-disable-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :set-doc-status override-auth-source]))
  :-> identity)

(rf/reg-sub ::approved?
  (fn approved-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::approvals/approved? doc-id])
     (delay (user/has-role?))])
  (fn [[approved? has-role?]]
    (and approved? has-role?)))

(rf/reg-sub ::rejected?
  (fn rejected-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::approvals/rejected? doc-id])
     (delay (user/has-role?))])
  (fn [[rejected? has-role?]]
    (and rejected? has-role?)))

(rf/reg-sub ::has-operation?
  (fn has-operation-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::docs/operation doc-id]))
  :-> some?)

(rf/reg-sub ::accordion-fields
  (fn accordion-fields-inputfn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::docs/schema-info doc-id]))
  :-> :accordion-fields)

(defn- field-values
  [doc-id field]
  (let [paths (or (:paths field) [field])]
    (->> paths
         (mapv (fn [path]
                 (let [path        (mapv keyword path)
                       value       (<sub [::committed/backend-submitted-value doc-id path])
                       ;; The path _might_ refer to a 'select' field, check if we
                       ;; have a map of the translation keys
                       options-map (<sub [::select-options/options-map doc-id (path/path-without-indexes path)])]
                   (if-let [loc-kw (get options-map value)]
                     (common/loc loc-kw)
                     value))))
         (keep ss/blank-as-nil))))

(defn- format-field
  [{:keys [localizeFormat format]} values]
  (when (seq values)
    (let [values (if localizeFormat
                   (map (fn [v] (common/loc (goos/format localizeFormat v))) values)
                   values)]
      (if format
        (->> (concat values (repeat 20 ""))
             (apply goos/format format))
        (ss/join-non-blanks " ")))))

(defn- process-work-period-field [doc-id field]
  (->> (field-values doc-id field)
       (map (fn [v] (common/finnish-date v)))
       (format-field field)))

(defn- process-selected-field [doc-id field]
  (->> (field-values doc-id
                     (update field
                             :paths
                             (fn [paths]
                               (let [selected (<sub [::committed/backend-submitted-value doc-id [:_selected]])]
                                 (filterv (fn [[fst & _]] (= fst selected)) paths)))))
       (format-field field)))

(defn- process-date-field [doc-id field]
  (->> (field-values doc-id field)
       ;; NOTE: Original js implementation did a string->date->string roundtrip through moment.js,
       ;; but here we just skip that as the values are already date strings.
       (format-field field)))

(defn- process-text-field [doc-id field]
  (->> (field-values doc-id field)
       (format-field field)))

(defn- process-field
  "See `fieldTypeFuns`"
  [doc-id field]
  (case (keyword (or (:type field) :text))
    :workPeriod (process-work-period-field doc-id field)
    :selected   (process-selected-field doc-id field)
    :date       (process-date-field doc-id field)
    :text       (process-text-field doc-id field)))

;; Fields are defined in document schema with :accordion-fields
(rf/reg-sub ::accordion-field-text
  (fn accordion-field-text-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::accordion-fields doc-id])])
  (fn [[fields] [_ doc-id]]
    (->> fields
         (keep (fn [field] (process-field doc-id field)))
         (ss/join-non-blanks " "))))

(rf/reg-sub ::header-description
  (fn header-description-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::identifiers/identifier-value doc-id])
     (rf/subscribe [::operation/current-description doc-id])
     (rf/subscribe [::accordion-field-text doc-id])])
  ;; See `headerDescription`
  (fn [[identifier description accordion]]
    (if (or (ss/not-blank? identifier)
            (ss/not-blank? description)
            (ss/not-blank? accordion))
      (let [init-str  " - "
            id-and-op (->> [identifier description]
                           (ss/join-non-blanks ": "))]
        (if (ss/not-blank? id-and-op)
          (str init-str (->> [id-and-op accordion]
                             (ss/join-non-blanks " ")))
          accordion))
      "")))

(rf/reg-sub ::disabled-text
  (fn disabled-text-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::docs/disabled? doc-id])
     (rf/subscribe [::docs/party-doc? doc-id])])
  (fn [[disabled? party?]]
    (when disabled?
      (str "(" (common/loc (if party?
                             :document.party.disabled
                             :document.disabled)) ")"))))

(rf/reg-sub ::accordion-header-text
  (fn accordion-header-text-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::header-description doc-id])
     (rf/subscribe [::disabled-text doc-id])])
  (fn [[header-text disabled-text]]
    (ss/join " " [header-text disabled-text])))

(rf/reg-sub ::show-toolbar?
  (fn show-toolbar-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    [(rf/subscribe [::may-remove? doc-id override-auth-source])
     (rf/subscribe [::approvals/has-value? doc-id])
     (rf/subscribe [::may-reject? doc-id override-auth-source])
     (rf/subscribe [::may-approve-or-reason doc-id override-auth-source])
     (rf/subscribe [::may-disable? doc-id override-auth-source])
     (rf/subscribe [::may-replace? doc-id])
     (rf/subscribe [::has-operation? doc-id])
     (rf/subscribe [::may-replace-catalog-operation? doc-id override-auth-source])])
  (fn [[show-remove? show-status? show-reject? show-approve? show-disable? show-replace? has-operation? show-replace-catalog?]]
    (and (user/has-role?)
         (or show-remove?
             show-status?
             show-reject?
             show-approve?
             show-disable?
             show-replace?
             has-operation?
             show-replace-catalog?))))

;;; Components

(defn- edit-note
  [doc-id]
  (when (<sub [::docs/post-verdict-edited? doc-id])
    [:span.form-approval-status.is-status.no-border-bottom
     {:class ["approved"
              (when (<sub [::has-operation? doc-id])
                "own-line")]}
     [:div.like-btn
      [:i.lupicon-circle-check.approved]
      [:span.is-details
       (<sub [::docs/post-verdict-edited-note doc-id])]]]))

(defn- sent-note
  [doc-id]
  (when (<sub [::docs/post-verdict-sent? doc-id])
    [:span.form-approval-status.is-status.no-border-bottom
     {:class ["approved"
              (when (<sub [::has-operation? doc-id])
                "own-line")]}
     [:div.like-btn
      [:i.lupicon-circle-check.approved]
      [:span.is-details
       (<sub [::docs/post-verdict-sent-note doc-id])]]]))

(defn- title
  "Document title"
  [doc-id]
  (let [title-kw (util/kw-path
                   (or (:name (<sub [::docs/operation doc-id]))
                       (:name (<sub [::docs/schema-info doc-id])))
                   :_group_label)]
    [:span.title
     (common/loc title-kw)]))

(defn- description
  "Optional short description after the title"
  [doc-id]
  (let [desc       (<sub [::accordion-header-text doc-id])
        shortened? false]
    [:span.description
     {:class [(when shortened?
                "shortenedDescription")]}
     desc]))

(defn- primary-star
  "The document describing the primary operation is highlighted with a ☆ symbol"
  [doc-id]
  [:span
   (when (<sub [::docs/primary-operation? doc-id])
     [:i.lupicon-star {:title (common/loc :operations.primary)}])])

(rf/reg-event-fx ::send-doc-updates
  (fn [_ [_ doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :send-doc-updates
                        :params  {:id    app-id
                                  :docId doc-id}
                        :success [::send-doc-updates.success doc-id]
                        :fail    (fn [err]
                                   (js/notify.ajaxError err))}})))

(rf/reg-event-fx ::send-doc-updates.success
  (fn [_ [_ _resp doc-id]]
    {:dispatch [::edit/toggle-edit-mode {:doc-id   doc-id
                                         :enabled? false}]}))

(defn- toggle-edit-section
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-toggle-edit? doc-id override-auth-source])
    (let [editing?              (<sub [::edit/editing-doc? doc-id])
          can-send-doc-updates? (<sub [::may-send-doc-updates? doc-id override-auth-source])]
      [:<>
       (when (not editing?)
         [:button.positive.is-right
          {:on-click (fn [_e] (>evt [::edit/toggle-edit-mode {:doc-id   doc-id
                                                              :enabled? true}]))}
          [:i.lupicon-pen]
          [:span (common/loc :document.edit)]])
       (when editing?
         [:button.secondary.is-right
          {:on-click (fn [_e] (>evt [::edit/toggle-edit-mode {:doc-id   doc-id
                                                              :enabled? false}]))}
          [:i.lupicon-remove]
          [:span (common/loc :document.edit.close)]])
       (when (and editing? can-send-doc-updates?)
         [:button.function.is-right
          {:on-click (fn [_e] (>evt [::send-doc-updates doc-id]))}
          [:i.lupicon-arrow-right]
          [:span (common/loc :document.edit.send)]])])))

(defn- identifier-editors
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::identifiers/show-identifier-editor? doc-id])
    (let [identifier-field? (<sub [::identifiers/identifier? doc-id])
          building-ref?     (<sub [::may-replace-catalog-operation? doc-id])]
      [:div.container-bubble
       (when (<sub [::app/archiving-project?])
         [:div.identifier-component.gap--t1
          [identifier-building/render (util/mmap doc-id override-auth-source)]])
       (when identifier-field?
         [:div.identifier-component.gap--t05.gap--r1.dsp--inline-block
          [identifier-doc/render (util/mmap doc-id override-auth-source)]])
       (if building-ref?
         [:div.identifier-component.gap--t05.dsp--inline-block
          [document-building-reference/render (util/mmap doc-id override-auth-source)]]
         [:div.identifier-component.gap--t05.dsp--inline-block
          [operation-editor/render (util/mmap doc-id override-auth-source)]])
       (when identifier-field?
         [common/loc-html-reagent :div.accordion-help.gap--t1 :document.operation-identifier.notice])])))

(defn- status-section
  [{:keys [doc-id disabled?]}]
  (when (<sub [::approvals/has-value? doc-id])
    (let [approved?      (<sub [::approved? doc-id])
          rejected?      (<sub [::rejected? doc-id])
          has-operation? (<sub [::has-operation? doc-id])]
      [:span.form-approval-status.is-status.no-border-bottom
       {:class [(when approved? "approved")
                (when rejected? "rejected")
                (when has-operation? "own-line")]}
       (when (or approved? rejected?)
         [:div.like-btn
          (when rejected?
            [:i.lupicon-circle-attention.rejected])
          (when approved?
            [:i.lupicon-circle-check.approved])
          [:span.is-details
           {:class [(when rejected? "is-details--rejected")]}
           (<sub [::approvals/details doc-id])]])
       [:div
        [reject-note/render {:doc-id       doc-id
                             :disabled?    disabled?
                             :note-class   [(if has-operation?
                                              "toolbar-reject-note"
                                              "accordion-reject-note")]
                             :editor-class [(if has-operation?
                                              "toolbar-reject-note-editor"
                                              "accordion-reject-note-editor")]}]]])))

(defn- approve-button
  "The `✓ OK` -button"
  [{:keys [doc-id override-auth-source]}]
  (when-let [show-approve-or-reason (<sub [::may-approve-or-reason doc-id override-auth-source])]
    (let [app-id (<sub [::app/application-id])
          error-reason (when (string? show-approve-or-reason)
                         show-approve-or-reason)]
      [:<>
       [:button.positive.is-right
        {:on-click (approval-common/on-approve (util/mmap app-id doc-id))
         :disabled (boolean error-reason)}
        [:i.lupicon-check]
        [:span (if (<sub [::use-designer-approval doc-id])
                 (common/loc :document.acknowledge)
                 (common/loc :document.approve))]]
       (when error-reason
         [:span.is-right
          (common/loc error-reason)])])))

(defn- reject-button
  "The `Tarkennettavaa` -button"
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-reject? doc-id override-auth-source])
    (let [app-id (<sub [::app/application-id])]
      [:button.secondary.is-right
       {:on-click (approval-common/on-reject (util/mmap app-id doc-id))}
       (common/loc :document.reject)])))

(rf/reg-event-fx ::change-operation
  (fn [{:keys [db]} [_ doc-id]]
    ;; Just redirect to the existing JS view,
    ;; it will return back to the regular application view (and trigger
    ;; application/load when ready)
    (let [app-id (common/application-id)
          schema-info (docs/db->doc-schema-info db doc-id)]
      {:page/open [:replace-operation app-id (:id (:op schema-info))]})))

(defn- replace-button
  "The `Vaihda toimenpidettä` -button"
  [{:keys [doc-id]}]
  (when (<sub [::may-replace? doc-id])
    [:button.secondary.is-right
     {:on-click (fn [_e] (>evt [::change-operation doc-id]))}
     [:i.lupicon-pen]
     [:span (common/loc :operations.replace)]]))

(defn- replace-catalog-operation-button
  "Operation reference for building product catalog and edit button for changing it"
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-replace-catalog-operation? doc-id override-auth-source])
    (let [doc          (<sub [::docs/building-document doc-id])
          target-state (not (<sub [::identifiers/show-identifier-editor? doc-id]))]
      [:button.secondary.is-right
       {:on-click (fn [_] (>evt [::identifiers/toggle {:doc-id   doc-id
                                                       :enabled? target-state}]))}
       [:i.lupicon-pen]
       [:span (document-building-reference/op-title doc)]])))

(rf/reg-event-fx ::remove-document
  (fn [{:keys [db]} [_ app-id doc-id]]
    (let [schema-info (docs/db->doc-schema-info db doc-id)
          doc-name (if-let [op (:op schema-info)]
                     (common/loc (str (:name op) "._group_label"))
                     (common/loc (str (:name schema-info) "._group_label")))
          message (str "<div>"
                       (common/loc :removeDoc.message1)
                       " <strong>" doc-name "</strong></div><div>"
                       (common/loc :removeDoc.message2)
                       "</div>")
          message (if (and (common/feature? :building-product-catalog)
                           (docs/db->document-referenced-by-low-carbon-assessment-document db doc-id))
                    (str message
                         "<strong>"
                         (common/loc :removeDoc.message.buildingProductCatalogNotification)
                         "</strong>")
                    message)]
      {:dialog/show {:type   :yes-no
                     :ltitle :areyousure
                     :size   :medium
                     :text   message
                     :event  [::remove-document.confirmed app-id doc-id]}})))

(rf/reg-event-fx ::remove-document.confirmed
  (fn [_ [_ app-id doc-id]]
    {:action/command {:name    :remove-doc
                      :params  {:id app-id
                                :docId doc-id
                                :collection nil}
                      :success [::remove-document.success app-id]
                      :error   [::remove-document.error app-id]}}))

(rf/reg-event-fx ::remove-document.success
  (fn [_ [_ _resp app-id]]
    {:fx [[:application/load app-id]]}))

(rf/reg-event-fx ::remove-document.error
  (fn [_ [_ error app-id]]
    (case (:text error)
      "error.document-not-found"
      {:fx [[:application/load app-id]]}

      "error.removal-of-last-document-denied"
      (do
        (js/notify.ajaxError (clj->js error))
        nil)

      nil)))

(defn- remove-button
  "The `Poista` -button"
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-remove? doc-id override-auth-source])
    (let [app-id (<sub [::app/application-id])]
      [:button.secondary.is-right
       {:on-click (fn [_e] (>evt [::remove-document app-id doc-id]))}
       [:i.lupicon-remove]
       [:span (common/loc :remove)]])))

(defn- operation-button
  "The `Tunnisteet` -button, which expands/collapses the identifier editor(s)."
  [{:keys [doc-id]}]
  (when (<sub [::has-operation? doc-id])
    (let [target-state (not (<sub [::identifiers/show-identifier-editor? doc-id]))]
      [:button.secondary.is-left
       {:on-click (fn [_] (>evt [::identifiers/toggle {:doc-id   doc-id
                                                       :enabled? target-state}]))}
       [:i.lupicon-pen]
       [:span (common/loc :document.operation-identifiers)]])))

(rf/reg-event-fx ::set-doc-status
  (fn [{:keys [db]} [_ doc-id value]]
    (let [etunimi  (docs/db->field-data-value db doc-id [:henkilotiedot :etunimi])
          sukunimi (docs/db->field-data-value db doc-id [:henkilotiedot :sukunimi])
          display-name (str etunimi " " sukunimi)]
      (if (= "enabled" value)
        {:dispatch [::set-doc-status.confirmed doc-id value]}
        {:dialog/show {:type   :yes-no
                       :ltitle :areyousure
                       :size   :medium
                       :text   (common/loc "document.party.disabled.areyousure" display-name)
                       :event  [::set-doc-status.confirmed doc-id value]
                       :component-params {:lyesTitle "document.party.disabled.areyousure.confirmation"
                                          :lnoTitle "cancel"}}}))))

(rf/reg-event-fx ::set-doc-status.confirmed
  (fn [_ [_ doc-id value]]
    (let [app-id (common/application-id)]
      {:action/command {:name :set-doc-status
                        :params {:id app-id
                                 :docId doc-id
                                 :value value}
                        :success [::set-doc-status.success app-id]}})))

(rf/reg-event-fx ::set-doc-status.success
  (fn [_ [_ resp app-id]]
    ;; NOTE: JS version does refresh disabled status and other
    ;; models without application/load
    {:fx [[:application/load app-id]
          [:indicator/icon resp]
          [:hub/send {:event "assignmentService::targetsQuery"
                      :data  {:applicationId app-id}}]
          [:hub/send {:event "assignmentService::applicationAssignments"
                      :data  {:applicationId app-id}}]]}))

(defn- toggle-disabled
  "The `Merkitse poistuneeksi` / `Palauta aktiiviseksi` button(s).

  At the moment used only with party documents in situations where
  e.g. a designer is no longer working on the project."
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-disable? doc-id override-auth-source])
    (let [disabled? (<sub [::docs/disabled? doc-id])]
      [:button.secondary.is-right
       {:on-click (fn [_e] (>evt [::set-doc-status doc-id (if disabled? "enabled" "disabled")]))}
       [:i {:class [(if disabled? "lupicon-check" "lupicon-remove")]}]
       [:span (common/loc (if disabled?
                            :document.status.toggle.enable
                            :document.status.toggle.disable))]])))

(rf/reg-sub ::show-assignments?
  (fn [[_ doc-id override-auth-source]]
    [(rf/subscribe [::auth/auth? doc-id :assignments-for-application override-auth-source])
     (rf/subscribe [::assignments/document-assignments doc-id])])
  (fn [[auth-ok? assignments]]
    (boolean (and auth-ok?
                  (seq assignments)))))

(rf/reg-sub ::show-print-foreman?
  (fn [[_ doc-id override-auth-source]]
    (rf/subscribe [::auth/auth? doc-id :pdf-foreman override-auth-source]))
  :-> identity)

(defn- foreman-document-pdf [doc-id override-auth-source]
  (when (<sub [::show-print-foreman? doc-id override-auth-source])
    [:a.btn.btn-primary
     {:id   (str "foreman-document-pdf-" doc-id)
      :target "_blank"
      :href (str "/api/raw/pdf-foreman?id=" (common/application-id) "&foreman-doc-id=" doc-id)}
      (common/loc :print)]))

(defn accordion-toolbar
  [{:keys [doc-id override-auth-source open? on-toggle]}]
  (let [focus-ref         (focus/use-focus doc-id)
        approved?         (<sub [::approved? doc-id])
        disabled?         (<sub [::docs/disabled? doc-id])
        show-toolbar?     (<sub [::show-toolbar? doc-id override-auth-source])
        summary-rejected? (<sub [::approvals/summary-rejected? doc-id])]
    [:div.accordion-toggle
     [:button
      {:class    [(when approved? "positive")
                  (when (not approved?) "plain")
                  (when summary-rejected? "rejected")
                  (when disabled? "disabled")
                  (when open? "toggled")]
       :on-click on-toggle
       :ref      focus-ref}
      [:i.lupicon-chevron-down]
      [:i.lupicon-chevron-up.toggle]
      [:span.bar-text
       [title doc-id]
       [description doc-id]
       [primary-star doc-id]
       [:span.icons
        [:i.lupicon-circle-attention.rejected]
        [:i.lupicon-circle-check.approved]]]]
     (when show-toolbar?
       [:div.group-buttons
        [status-section {:doc-id    doc-id
                         :disabled? disabled?}]
        [edit-note doc-id]
        [sent-note doc-id]
        [operation-button {:doc-id doc-id}]
        [remove-button {:doc-id               doc-id
                        :override-auth-source override-auth-source}]
        [replace-button {:doc-id doc-id}]
        [replace-catalog-operation-button {:doc-id               doc-id
                                           :override-auth-source override-auth-source}]
        [toggle-disabled {:doc-id               doc-id
                          :override-auth-source override-auth-source}]
        [reject-button {:doc-id               doc-id
                        :override-auth-source override-auth-source}]
        [approve-button {:doc-id               doc-id
                         :override-auth-source override-auth-source}]
        [foreman-document-pdf doc-id override-auth-source]
        [toggle-edit-section {:doc-id               doc-id
                              :override-auth-source override-auth-source}]
        [identifier-editors {:doc-id               doc-id
                             :override-auth-source override-auth-source}]])
     (when (<sub [::show-assignments? doc-id override-auth-source])
       [accordion-assignments/render (util/mmap doc-id)])]))
