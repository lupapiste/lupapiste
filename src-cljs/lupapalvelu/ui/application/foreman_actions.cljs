(ns lupapalvelu.ui.application.foreman-actions
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.application.foreman-termination :as foreman-termination]
            [lupapalvelu.ui.application.foreman-util :as foreman-util]
            [lupapalvelu.ui.application.util :as application-util]
            [lupapalvelu.ui.authorization :as js-auth]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.user :as user]
            [re-frame.core :as rf]
            [schema.core :as sc]))

(def default-doc-type :notification)

(rf/reg-event-fx ::navigate-to-foreman-verdict-view
  (fn [_ [_ app-id foreman-doc-id]]
    {:fx [[:dispatch [:application/open app-id :suffix :verdict]]
          [:focus-on-element [(str "[data-foreman-id='" foreman-doc-id "']") 100]]]}))

(defn foreman-actions [{tj-doc-id :doc-id
                        :keys     [app-id doc-state initial-doc-type
                                   subscribe-doc-auth
                                   subscribe-current-user-authority?
                                   subscribe-current-user-applicant?
                                   subscribe-termination-req-allowed open-terminate-foreman]}]
  (let [;; Get the current value first from form state, then from document and then default.
        doc-type                 (or (<sub [::data/foreman-doc-type tj-doc-id])
                                     initial-doc-type)
        show-declaration-button? (and (not doc-state)
                                      (or (subscribe-current-user-authority?)
                                          (subscribe-current-user-applicant?)))
        can-change-doc-type?     (subscribe-doc-auth :change-foreman-doc-type)
        can-set-doc-type?        (not doc-state)]
    [:div.m-2
     [:div
      [:div
       [components/dropdown
        doc-type
        {:id        (str "type-" tj-doc-id)
         :items     [{:text (common/loc "permitSubtype.tyonjohtaja-ilmoitus") :value :notification}
                     {:text (common/loc "permitSubtype.tyonjohtaja-hakemus") :value :application}]
         :required? true
         :choose?   false ; don't show empty option
         :sort-by   :text
         :test-id   :value
         :class     "mr-2"
         :enabled?  (boolean (or can-change-doc-type?
                                 can-set-doc-type?))
         :callback  (if can-change-doc-type?
                      #(>evt [::data/change-foreman-doc-type tj-doc-id %])
                      #(>evt [::data/set-foreman-doc-type tj-doc-id %]))}]
       (components/help-toggle {:text-loc :foreman.type-selection.help})]
      [:div.flex.justify-between
       [:div
        (when show-declaration-button?
          [:button.primary.mr-2
           {:disabled (not (subscribe-doc-auth :declare-foreman))
            :on-click (fn []
                        (components/confirm-dialog
                          "areyousure"
                          (if (= doc-type :notification)
                            "foreman.declaration.confirm"
                            "foreman.application.confirm")
                          #(>evt [(if (= doc-type :notification)
                                    ::data/declare-foreman
                                    ::data/submit-foreman) tj-doc-id])))}
           (common/loc (if (= doc-type :notification)
                         :foreman.declare
                         :foreman.submit))])
        (when (subscribe-doc-auth :acknowledge-foreman)
          [:button.primary.mr-2
           {:on-click (fn []
                        (components/confirm-dialog
                          "areyousure"
                          "foreman.acknowledge.confirm"
                          #(>evt [::data/acknowledge-foreman tj-doc-id])))}
           (common/loc :foreman.acknowledge)])
        (when (subscribe-doc-auth :approve-foreman)
          [:button.primary.mr-2
           {:on-click (fn []
                        (components/confirm-dialog
                          "areyousure"
                          "foreman.approve.confirm"
                          #(>evt [::data/approve-foreman tj-doc-id])))}
           (common/loc :foreman.approve)])
        (when (subscribe-termination-req-allowed)
          [:button.secondary
           {:on-click open-terminate-foreman}
           (common/loc :foreman.terminate)])
        (when (or (subscribe-doc-auth :new-pate-foreman-verdict-draft)
                  (subscribe-doc-auth :new-legacy-foreman-verdict-draft))
          [:button.tertiary
           {:on-click #(>evt [::navigate-to-foreman-verdict-view app-id tj-doc-id])}
           (common/loc (if (and (subscribe-current-user-authority?)
                                (= doc-state "submitted"))
                         :foreman.give-verdict
                         :foreman.navigate-to-verdict))])]
       (when (subscribe-doc-auth :send-foreman-message)
         [:button.tertiary.self-end
          {:on-click (fn []
                       (components/confirm-dialog
                         "areyousure"
                         "foreman.send-message.confirm"
                         #(>evt [::data/send-foreman-message tj-doc-id])))}
          (common/loc :foreman.send-message)])]]]))

;; JS entrypoint

(defn view [{:keys [document]}]
  (let [doc-id       (:id document)
        authModel    (.. js/lupapisteApp -services -documentDataService (findDocumentById doc-id) -auth)
        can-declare? (application-util/js-use-auth-model-result authModel "declare-foreman")]
    (react/useEffect (fn []
                       (>evt [:auth/category-refresh])
                       (>evt [:auth/refresh])
                       (.refreshAuthModel js/lupapisteApp.services.documentDataService doc-id)
                       js/undefined)
                     #js [doc-id])
    [foreman-actions
     {:app-id                            (common/application-id)
      :doc-id                            doc-id
      :doc-state                         (:state document)
      :initial-doc-type                  (or (some-> document :type keyword)
                                             default-doc-type)
      :subscribe-doc-auth                (fn [action-name]
                                           ;; There is a risk that :auth/document? sub isn't
                                           ;; up-to-date when some command returns an updated
                                           ;; authModel. Use hook to observe authModel status.
                                           (if (= :declare-foreman action-name)
                                             can-declare?
                                             (<sub [:auth/document? doc-id action-name])))
      :subscribe-current-user-authority? (fn [] (user/authority?))
      :subscribe-current-user-applicant? (fn [] (user/applicant?))
      :subscribe-termination-req-allowed (fn []
                                           (foreman-util/termination-req-allowed
                                             {:doc-state               (:state document)
                                              :termination             (:foremanTermination document)
                                              :command-authorized      (or (js-auth/application-auth? :request-foreman-termination)
                                                                           (js-auth/application-auth? :terminate-foreman))
                                              :foreman-is-current-user (= (:email document) (.email (user/current-user)))
                                              :is-authority            (user/authority?)
                                              :is-applicant            (user/applicant?)}))
      :open-terminate-foreman            (fn [_e] (>evt [::data/open-terminate-foreman nil document foreman-termination/termination-dialog]))}]))

;; Wrap for functional component
(defn view* [params]
  [:f> view params])

(defonce mounter (ui-dom/init :reagent (fn [] view*)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id (-> params common/->cljs)))

;; Cljs entrypoint

(sc/defn render
  "Native docgen CLJS component"
  [{:keys [app-id doc-id]} :- schemas/DocgenFieldArguments]
  (let [document (<sub [::docs/document doc-id])]
    [foreman-actions
     {:app-id                            app-id
      :doc-id                            doc-id
      :doc-state                         (:state (<sub [::docs/document doc-id]))
      :initial-doc-type                  (or (some-> (:type (<sub [::docs/document doc-id])) keyword)
                                             default-doc-type)
      :subscribe-doc-auth                (fn [action-name] (<sub [::auth/doc-auth? doc-id action-name]))
      :subscribe-current-user-authority? (fn [] (<sub [::auth/current-user-is-authority]))
      :subscribe-current-user-applicant? (fn [] (<sub [::auth/current-user-is-applicant]))
      :subscribe-termination-req-allowed (fn []
                                           (foreman-util/termination-req-allowed
                                             {:doc-state               (:state document)
                                              :termination             (:foremanTermination document)
                                              :command-authorized      (or (js-auth/application-auth? :request-foreman-termination)
                                                                           (js-auth/application-auth? :terminate-foreman))
                                              :foreman-is-current-user (= (:email document) (<sub [::session/email]))
                                              :is-authority            (<sub [::auth/current-user-is-authority])
                                              :is-applicant            (<sub [::auth/current-user-is-applicant])}))
      :open-terminate-foreman            (fn [_e] (>evt [::data/open-terminate-foreman nil document foreman-termination/termination-dialog]))}]))
