(ns lupapalvelu.ui.docgen.field.docgen-person-select
  "See `docgen-person-select-[template.html, model.js]`."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.docgen.component.authorize-person-dialog :as auth-dialog]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.parties :as parties]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def set-user-cmd :set-user-to-document)

(defn- invite-button
  [{:keys [app-id doc-id disabled? override-auth-source path]}]
  (when (and (not disabled?)
             (<sub [::auth/auth? doc-id :invite-with-role override-auth-source]))
    (let [args {:app-id   app-id
                :doc-id   doc-id
                :doc-name (<sub [::docs/doc-name doc-id])
                :path     path}]
      [:button.positive
       {:on-click (fn [_] (>evt [::auth-dialog/show-invite-dialog args]))}
       (common/loc :personSelector.invite)])))

(defn- empty-option
  []
  {:value ""
   :text (common/loc :selectone)})

(defn- full-name [user]
  (->> user
       ((juxt :firstName :lastName))
       (ss/join-non-blanks " ")))

(rf/reg-sub ::choices
  (fn choices-inputfn [[_ exclude-companies?]]
    {:pre [(boolean? exclude-companies?)]}
    (rf/subscribe [::parties/person-items exclude-companies?]))
  (fn [items]
    (->> items
         (map (fn [{:keys [id company] :as item}]
                (let [fname (full-name item)]
                  {:value id
                   :text  (if company
                            (str fname ", " (:name company))
                            fname)})))
         (sort-by (juxt :text :value))
         (into [(empty-option)]))))

(defn- can-set-user?
  [{:keys [doc-id override-auth-source]}]
  (<sub [::auth/auth? doc-id set-user-cmd override-auth-source]))

(defn- set-user-fx
  [_ [_ {:keys [app-id doc-id path value]}]]
  (let [;; The `set-user-to-document` command takes a path to the parent
        path (pop path)]
    {:fx [[:action/command {:name    set-user-cmd
                            :params  {:id         app-id
                                      :documentId doc-id
                                      :userId     value
                                      :path       (path/path->str path)
                                      :collection :documents}
                            :success [::set-user.success app-id]}]]}))

(defn- db->fixed-email
  [db doc value]
  (let [schema-info  (-> doc :schema :info)
        designer?    (and (= "party" (:type schema-info))
                          (= "suunnittelija" (:subtype schema-info)))
        company-user (parties/db->company-user db value)]
    (when (and (ss/not-blank? value)
               designer?
               (not (:personally-authorized company-user)))
      (:email company-user))))

(rf/reg-event-fx ::auth-id-changed
  (fn [{:keys [db] :as cofx} [_ {:keys [app-id doc-id path value]}]]
    (let [doc         (docs/db->document db doc-id)
          schema-info (-> doc :schema :info)
          fixed-email (db->fixed-email db doc value)]
      (nutil/merge-every-fn
        cofx
        [;; Store updated value so that the select value doesn't flicker
         [value/fields-edited-fx {:doc-id      doc-id
                                 :field-paths [path]
                                 :values      [value]}]
         ;; Show dialog or apply command
         (if fixed-email
           [auth-dialog/show-invite-dialog-fx {:app-id   app-id
                                               :auth-id  value
                                               :doc-id   doc-id
                                               :doc-name (:name schema-info)
                                               :email    fixed-email
                                               :path     path}]
           [set-user-fx (util/mmap app-id doc-id path value)])]))))

(rf/reg-event-fx ::set-user.success
  (fn [_ [_ _resp app-id]]
    {:fx [[:application/load app-id]]}))

(sc/defn render
  "See `docgen-person-select[-template.html|-model.js]`.
   Also checkout `docgen-input-model.js`."
  [{:keys [app-id doc-id schema path override-auth-source disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [excludeCompanies]} schema
        validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        input-id                   (path/path->id path doc-id)
        read-only?                 (or disabled? (not (can-set-user? args)))
        current-value              (<sub [::value/current-field-value doc-id path])
        choices                    (<sub [::choices (boolean excludeCompanies)])]
    [:div.form-entry.personSelector
     [:div.person-select-box
      [:label
       {:class ["lux" "form-label" validation-result-level]
        :for   input-id}
       (common/loc :person-selector)]
      [reselect/reselect
       current-value
       {:id        input-id
        :class     ["long" "inline-block" "align-middle"]
        :multi?    false
        :disabled? read-only?
        :items     choices
        :callback  (fn [value]
                     (>evt [::auth-id-changed (util/mmap app-id doc-id path value)]))}]
      " "
      [invite-button (util/mmap app-id doc-id disabled? path override-auth-source)]]]))
