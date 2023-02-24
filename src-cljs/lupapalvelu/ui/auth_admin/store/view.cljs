(ns lupapalvelu.ui.auth-admin.store.view
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [lupapalvelu.ui.misc :as misc]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]))


(defn valid-email? [db]
  (let [email (some-> db ::configuration :email ss/trim)]
    (or (ss/blank? email)
        (js/util.isValidEmailAddress email))))

(rf/reg-event-fx
  ::fetch-configuration
  (fn [{db :db} _]
    {:action/query {:name    :document-request-info
                    :params  {:organizationId (::organization-id db)}
                    :success [:action/store-response :documentRequest
                              ::configuration]}}))

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

(rf/reg-sub
  ::valid-email?
  (fn [db _]
    (valid-email? db)))

(defn instructions [lang]
  (let [authed?      (<sub [:auth/global? :set-document-request-info])
        valid-email? (<sub [::valid-email?])
        lang-kw      (keyword lang)
        markup       (<sub [:value/get ::configuration :instructions lang-kw])
        id           (str "request-info-" lang)]
        [:div.flex--column.flex--gap05
         [:label.lux.h3 {:for id} (loc (str "in_" lang))]
         [components/markup-edit markup
          {:id        id
           :disabled (not (and authed? valid-email?))
           :callback  #(>evt [::edit-configuration :instructions lang-kw %])}]]))

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

(defn view []
  (r/with-let [_         (>evt [::fetch-configuration])
               languages (common/->cljs (js/loc.getSupportedLanguages))]
    (let [enabled?     (<sub [:value/get ::configuration :enabled])
          authed?      (<sub [:auth/global? :set-document-request-info])
          valid-email? (<sub [::valid-email?])]
      [:div.flex--column.flex--gap2
       [:h1.txt--bold (loc :auth-admin.docstore.title)]
       [components/toggle enabled?
        {:text-loc  :auth-admin.docstore.document-request.enabled
         :disabled? (not (and authed? valid-email?))
         :prefix    :blockbox
         :callback  #(>evt [::edit-configuration :enabled %])}]

       (when enabled?
         [:div.flex--column.flex--gap2
          [email]
          (into [:<>
                 [:h2 (loc :auth-admin.docstore.document-request.instructions)]]
                (for [lang languages]
                  [instructions lang]))])])))

(defn mount-component [dom-id]
  (rd/render [view] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (>evt [:value/set ::organization-id (common/->cljs (common/oget params "orgId"))])
  (mount-component dom-id))
