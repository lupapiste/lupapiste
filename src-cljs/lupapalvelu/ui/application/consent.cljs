(ns lupapalvelu.ui.application.consent
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.application.util :as application-util]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [lupapalvelu.ui.util :as ui-util]
            [sade.strings :as ss]
            [schema.core :as sc]
            [lupapalvelu.ui.docgen.component.help-toggle :as help-toggle]))

(defn js-subscribe-email [app-id doc-id initial-email]
  (let [[email set-email] (react/useState initial-email)]
    (react/useEffect (fn []
                       (let [^js sub (.subscribe js/hub #js {:eventType "docgen-email-updates"
                                                             :docId doc-id}
                                                 (fn [^js e] (set-email (.-value e))))]
                         (fn [] (.unsubscribe js/hub sub))))
                     #js [app-id doc-id])
    email))

;; Cljs docgen can use the same ::data/{foreman,designer}-consent fx to update
;; the value, because the consent given status is retrivied using pseudo-query
;; status from auth-model. No need to use the value feature, which stores the new
;; value to app-db for local use.
;; NOTE: Consider using value feature, and dropping the pseudo-query later?
(defn view [{:keys [app-id doc-id field-name subscribe-email subscribe-auth-model current-user-email]}]
  (let [{:keys [i18n-prefix consent-given-pseudo-query consent-command-name consent-event]}
        (case field-name
          "foremanConsent"
          {:i18n-prefix "foreman."
           :consent-given-pseudo-query "foreman-consent-given"
           :consent-command-name "foreman-consent"
           :consent-event ::data/foreman-consent}
          "designerConsent"
          {:i18n-prefix "osapuoli.suunnittelija."
           :consent-given-pseudo-query "designer-consent-given"
           :consent-command-name "designer-consent"
           :consent-event ::data/designer-consent})

        email             (subscribe-email app-id doc-id)
        consent-given     (subscribe-auth-model consent-given-pseudo-query)
        ;; Check if user can give the consent (current user is the chosen designer/foreman etc.)
        ;; Update-doc automatically refreshes authModel so changing the email will affect this.
        consent-allowed   (subscribe-auth-model consent-command-name)]
    ;; NOTE: consent component is only used under tj-v3 doc root,
    ;; add form-group here because the field isn't under any group,
    ;; but it looks better that we match the padding from group class.
    [:div.row.form-group
     (when-not consent-given
       [:div.help-text.help-text--show
        [common/loc-html-reagent
         :p
         (str i18n-prefix "give-consent.detail")
         (or (ss/blank-as-nil email)
             (common/escape-html (common/loc "<Arvo puuttuu>")))
         (current-user-email)]])
     [components/toggle
      consent-given
      {:id        (str doc-id "-consent")
       :callback  #(>evt [consent-event doc-id %])
       :disabled? (or consent-given
                      (not consent-allowed))
       :text-loc  (keyword (str i18n-prefix "give-consent"))
       :class "align-middle"}]
     [help-toggle/help-toggle
      {:toggle-class "align-middle"
       :help-content (common/loc (str i18n-prefix "give-consent.help"))}]]))

;; Cljs docgen entrypoint

(sc/defn render
  "Native docgen CLJS component"
  [{:keys [app-id doc-id schema]} :- schemas/DocgenFieldArguments]
  (let [field-name (:name schema)]
    [:f> view
     {:app-id app-id
      :doc-id doc-id
      :field-name field-name
      :current-user-email (fn [] (<sub [::session/email]))
      :subscribe-email (fn [_app-id doc-id] (<sub [::value/current-field-value doc-id [:yhteystiedot :email]]))
      :subscribe-auth-model (fn [op-name] (<sub [::auth/doc-auth? doc-id (keyword op-name)]))}]))

;; JS docgen entrypoint

(defn view* [{:keys [applicationId documentId initialEmail _authModel fieldName _email]}]
  (let [authModel (.. js/lupapisteApp -services -documentDataService (findDocumentById documentId) -auth)]
    [:f> view
     {:app-id applicationId
      :doc-id documentId
      :field-name fieldName
      :current-user-email (fn [] (ui-util/get-user-field "email"))
      :subscribe-email (fn [app-id doc-id] (js-subscribe-email app-id doc-id initialEmail))
      :subscribe-auth-model (fn [op-name] (application-util/js-use-auth-model-result authModel op-name))}]))

(defonce mounter (ui-dom/init :reagent (fn [] view*)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id (js->clj params :keywordize-keys true)))
