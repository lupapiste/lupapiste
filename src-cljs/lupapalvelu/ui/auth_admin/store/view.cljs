(ns lupapalvelu.ui.auth-admin.store.view
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.shared-docstore :as docstore]
            [lupapalvelu.shared-quill :as quill]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.quill :as quill-comp]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc :as misc]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]))


(defn valid-email? [db]
  (let [email (some-> db ::configuration :email ss/trim)]
    (or (ss/blank? email)
        (js/util.isValidEmailAddress email))))


(rf/reg-event-fx
  ::fetch-configuration
  (fn [{db :db} _]
    {:action/query {:name    :document-request-info
                    :params  {:organizationId (::organization-id db)}
                    :success [:action/store-response :documentRequest ::configuration]}}))

(rf/reg-event-fx
  ::save-configuration
  (fn [{db :db}]
    (when (valid-email? db)
      {:action/command
       {:name                  :set-document-request-info
        :params                (assoc (::configuration db)
                                      :organizationId (::organization-id db))
        :show-saved-indicator? true
        :success               identity}})))

(rf/reg-event-fx
  ::edit-configuration
  (fn [{db :db} [_ & kvs]]
    {:db                (misc/set-value db (cons ::configuration kvs))
     :dispatch-debounce {:id      ::save-configuration
                         :timeout 1000
                         :action  :dispatch
                         :event   [::save-configuration]}}))

(rf/reg-event-fx
  ::edit-instruction
  (fn [{db :db} [_ lang quill-value]]
    (let [lang (keyword lang)]
      (if-let [error (quill/check-images quill-value)]
        {:db (assoc-in db [::errors lang] error)}
        {:db (update db ::errors dissoc lang)
         :dispatch [::edit-configuration :instructions
                    lang quill-value]}))))

(rf/reg-event-fx
  ::save-setting-flag
  (fn [{db :db} [_ command-name enabled]]
    {:action/command {:name                  command-name
                      :params                {:organizationId (::organization-id db)
                                              :enabled        enabled}
                      :show-saved-indicator? true
                      :success               identity}}))

(rf/reg-sub
  ::valid-email?
  (fn [db _]
    (valid-email? db)))

(rf/reg-sub
  ::instruction-error
  (fn [db [_ lang]]
    (get-in db [::errors (keyword lang)])))

(rf/reg-sub
  ::instruction
  (fn [db [_ lang]]
    (get-in db [::configuration :instructions (keyword lang)])))


(defn instructions [lang]
  (let [authed?      (<sub [:auth/global? :set-document-request-info])
        valid-email? (<sub [::valid-email?])
        content      (<sub [::instruction lang])
        id           (str "request-info-" lang)]
    [:div.flex--column.flex--gap05
     [:label.lux.h3 {:for id} (loc (str "in_" (name lang)))]
     [quill-comp/quill-edit content
      {:callback  #(>evt [::edit-instruction lang %])
       :disabled? (not (and authed? valid-email?))
       :id        id}]
     (when-let [error (<sub [::instruction-error lang])]
       [:div.error-note (loc error)])]))

(defn email []
  (let [authed?      (<sub [:auth/global? :set-document-request-info])
        valid-email? (<sub [::valid-email?])
        email        (<sub [:value/get ::configuration :email])]
    [:div.flex--column.flex--gap05.w--max-40em
     [:label.lux.txt--bold
      {:for "document-request-email"}
      (loc :auth-admin.docstore.document-request.email)]
     [components/text-edit email
      {:id       "document-request-email"
       :invalid? (not valid-email?)
       :disabled (not authed?)
       :callback #(>evt [::edit-configuration :email %])}]]))

(defn document-request-config []
  (let [enabled?     (<sub [:value/get ::configuration :enabled])
        authed?      (<sub [:auth/global? :set-document-request-info])
        valid-email? (<sub [::valid-email?])]
    [:<>
     [components/toggle enabled?
      {:text-loc  :auth-admin.docstore.document-request.enabled
       :disabled? (not (and authed? valid-email?))
       :prefix    :blockbox
       :callback  #(>evt [::edit-configuration :enabled %])}]
     (when enabled?
       [:div
        [:div.flex--column.flex--gap2
         [email]
         (into [:<>
                [:h2 (loc :auth-admin.docstore.document-request.instructions)]]
               (for [lang docstore/docstore-languages]
                 [instructions lang]))]])]))

(defn docstore-config
  "Docstore configuration options not related to the document request"
  []
  (let [preview-login? (<sub [:auth/global? :login-required-for-previews])]
    [components/toggle preview-login?
     {:text-loc  :auth-admin.docstore.login-required-for-previews.enabled
      :prefix    :blockbox
      :disabled? (not (<sub [:auth/global? :set-organization-login-required-for-previews]))
      :callback  #(>evt [::save-setting-flag :set-organization-login-required-for-previews (not preview-login?)])}]))

(defn view []
  (r/with-let [_ (>evt [::fetch-configuration])]
    [:div.flex--column.flex--gap2
     [:h1.txt--bold (loc :auth-admin.docstore.title)]
     [:div (loc :auth-admin.docstore.help)]
     [docstore-config]
     [document-request-config]]))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id params]
  (>evt [:value/set ::organization-id (common/->cljs (common/oget params "orgId"))])
  (ui-dom/mount! mounter dom-id))
