(ns lupapalvelu.ui.docgen.field.company-selector
  "See `company-selector-[template.html, model.js]`."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.docgen.component.company-invite-dialog :as company-dialog]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.parties :as parties]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc]))

(def set-company-cmd :set-company-to-document)

(defn- invite-button
  [{:keys [app-id doc-id override-auth-source path]}]
  (when (<sub [::auth/auth? doc-id :invite-with-role override-auth-source])
    (let [args {:app-id   app-id
                :doc-id   doc-id
                :doc-name (<sub [::docs/doc-name doc-id])
                :path     path}]
      [:button.btn-primary
       {:on-click (fn [_] (>evt [::company-dialog/show-invite-dialog args]))}
       [:i.lupicon-circle-plus]
       [:span (common/loc :company-invite.invite)]])))

(defn- can-set-company?
  [{:keys [doc-id override-auth-source]}]
  (<sub [::auth/auth? doc-id set-company-cmd override-auth-source]))

(defn- empty-option
  []
  {:value ""
   :text  (common/loc :selectone)})

(rf/reg-sub ::choices
  (fn choices-inputfn [_]
    (rf/subscribe [::parties/company-roles]))
  (fn [companies]
    (->> companies
         (map (fn [{:keys [id y] :as company}]
                {:value id
                 :text  (str (:name company) " (" y ")")}))
         (sort-by (juxt :text :value))
         (into [(empty-option)]))))

(rf/reg-event-fx ::update-document.success
  (fn [_ [_ _resp app-id]]
    {:pre [(string? app-id)]}
    {:fx [[:application/load app-id]]}))

(defn- set-company-fx
  [_ [_ {:keys [app-id doc-id path value]}]]
  ;; Unlike `set-user-to-document`, the update here happens in three steps:
  ;; 1) execute `set-company-to-document`, which updates the address etc but not the actual `companyId`.
  ;; 2) update the actual selector value (`companyId`) with `update-doc`
  ;; 3) refresh the app data
  {:fx [[:action/command {:name    set-company-cmd
                          :params  {:id         app-id
                                    :documentId doc-id
                                    :companyId  value
                                    ;; The `set-company-to-document` command takes a path to the parent
                                    :path       (path/path->str (pop path))}
                          :success (fn [_]
                                     (>evt [::command/update-doc {:app-id      app-id
                                                                  :doc-id      doc-id
                                                                  :field-paths [path]
                                                                  :values      [value]
                                                                  :on-success  [::update-document.success app-id]}]))}]]})

(rf/reg-event-fx ::company-changed
  (fn [cofx [_ {:keys [app-id doc-id path value]}]]
    (nutil/merge-every-fn
      cofx
      [;; Store updated value so that the select value doesn't flicker
       [value/fields-edited-fx {:doc-id      doc-id
                               :field-paths [path]
                               :values      [value]}]
       [set-company-fx (util/mmap app-id doc-id path value)]])))

(sc/defn render
  "See `company-selector-[template.html|model.js]`.
   Also checkout `docgen-input-model.js`.
   And `docmodel.js` / `buildCompanySelector()`."
  [{:keys [app-id doc-id schema path override-auth-source disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [excludeCompanies]} schema
        validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        input-id                   (path/path->id path doc-id)
        read-only?                 (or disabled? (not (can-set-company? args)))
        current-value              (<sub [::value/current-field-value doc-id path])
        choices                    (<sub [::choices (boolean excludeCompanies)])]
    [docgen-common/entry-span
     (assoc args :extra-classes ["companySelector"])
     [:div
      [:label
       {:class ["form-label" "form-label-select" validation-result-level]
        :for   input-id}
       (common/loc :company-selector.title)]
      [reselect/reselect
       current-value
       {:id        input-id
        :class     ["long" "inline-block" "align-middle"]
        :disabled? read-only?
        :items     choices
        :callback  (fn [value]
                     (>evt [::company-changed (util/mmap app-id doc-id path value)]))}]
      " "
      [invite-button (util/mmap app-id doc-id path override-auth-source)]]]))
