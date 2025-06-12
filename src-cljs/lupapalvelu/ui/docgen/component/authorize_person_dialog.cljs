(ns lupapalvelu.ui.docgen.component.authorize-person-dialog
  "See `authorize-person-dialog-model.js`."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.util :as ui-util]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::doc-id :<- [::data] :-> :doc-id)
(rf/reg-sub ::email :<- [::data] :-> :email)
(rf/reg-sub ::fixed-email? :<- [::data] :-> :fixed-email?)

(rf/reg-sub ::user-email :<- [::data] :-> :user-email)
(rf/reg-sub ::user-text :<- [::data] :-> :user-text)

(rf/reg-sub ::all-disabled? :<- [::data] :-> (comp boolean :all-disabled?))
(rf/reg-sub ::show-remove? :<- [::data] :-> (comp boolean :show-remove?))

(rf/reg-sub ::error :<- [::data] :-> :error)

(rf/reg-sub ::valid-email?
  :<- [::fixed-email?]
  :<- [::user-email]
  (fn [[fixed? email]]
    (or fixed?
        (-> email
            ss/canonize-email
            ui-util/valid-email?))))

(rf/reg-sub ::invite-disabled?
  :<- [::all-disabled?]
  :<- [::valid-email?]
  :<- [::user-text]
  (fn [[disabled? valid-email? text]]
    (or disabled?
        (not valid-email?)
        (ss/blank? text))))

;; Fields

(defn- description-field
  []
  (let [description (if (<sub [::fixed-email?])
                      :document.party.person-select.designer.areyousure
                      :invite.desc)]
    [:p (common/loc description)]))

(rf/reg-event-fx ::email-changed
  (fn [{:keys [db]} [_ email]]
    {:db (assoc-in db [::data :user-email] email)}))

(defn- email-field
  []
  (let [doc-id   (<sub [::doc-id])
        email-id (str doc-id "-auth-email")
        fixed?   (<sub [::fixed-email?])]
    [:<>
     [:label {:for email-id}
      (common/loc :userinfo.email)]
     [ui-input/text-input
      {:id        email-id
       :class     "grid-style-input"
       :value     (if fixed?
                    (<sub [::email])
                    (<sub [::user-email]))
       :disabled? (or (<sub [::all-disabled?])
                      fixed?)
       :on-change (fn [value] (>evt [::email-changed value]))}]]))

(rf/reg-event-fx ::text-changed
  (fn [{:keys [db]} [_ text]]
    {:db (assoc-in db [::data :user-text] text)}))

(defn- text-field
  []
  (let [doc-id  (<sub [::doc-id])
        text-id (str doc-id "-auth-text")]
    [:<>
     [:label {:for text-id}
      (common/loc :application.inviteMessage)]
     [ui-input/textarea
      {:id          text-id
       :rows        10
       :placeholder (common/loc :comment.placeholder)
       :disabled?   (<sub [::all-disabled?])
       :value       (<sub [::user-text])
       :on-change   (fn [value] (>evt [::text-changed value]))}]]))

(defn- error-field
  []
  (when-let [error (<sub [::error])]
    [:div.context-error
     (common/loc error)]))

(defn- cleanup-errors-fx
  [{:keys [db]} _]
  {:db (-> db
           (update ::data dissoc :error :show-remove? :all-disabled?))})

(defn- reload-fx
  [_ [_ app-id]]
  {:fx [[:application/load app-id]]})

(defn- refresh-guests-fx
  [_ [_ _]]
  {:fx [[:hub/send {:event :refresh-guests}]]})

(rf/reg-event-fx ::remove-clicked
  (fn [{:keys [db]} _]
    (let [{:keys [app-id auth-id]} (::data db)]
      {:db (assoc-in db [::data :all-disabled?] true)
       :fx [[:action/command {:name      :remove-auth
                              :params    {:id     app-id
                                          :authId auth-id}
                              :success   [::remove.success app-id]
                              :error     [::command.error]
                              :timed-out [::command.error]}]]})))

(rf/reg-event-fx ::remove.success
  (fn [cofx [_ _resp app-id]]
    (nutil/merge-every-fn cofx
                          [[reload-fx app-id]
                           [cleanup-errors-fx]
                           [refresh-guests-fx]])))


(defn- remove-button
  []
  (when (<sub [::show-remove?])
    [:<>
     [:p (common/loc :document.party.person-select.remove-existing-role-before-invite)]
     [ui/icon-button
      {:icon     :circle-remove
       :class    :negative
       :text-loc :document.party.person-select.remove-auth
       :wait?    (<sub [::all-disabled?])
       :on-click (fn [e]
                   (.preventDefault e)
                   (>evt [::remove-clicked]))}]]))

(defn- cleanup-fx
  [{:keys [db]} [_]]
  {:db (dissoc db ::data)})

(rf/reg-event-fx ::dialog-closed
  cleanup-fx)

(defn- close-dialog-fx
  [_ [_ _]]
  {:fx [[:hub/send {:event :close-dialog}]]})

(rf/reg-event-fx ::invite-clicked
  (fn [{:keys [db]} _]
    (let [{:keys [app-id doc-id doc-name path
                  fixed-email? email user-email
                  user-text]} (::data db)
          email               (if fixed-email? email user-email)]
      {:db (assoc-in db [::data :all-disabled?] true)
       :fx [[:action/command {:name      :invite-with-role
                              :params    {:id           app-id
                                          :documentName doc-name
                                          :documentId   doc-id
                                          :path         (path/path->str (pop path))
                                          :email        (ss/canonize-email email)
                                          :text         (ss/trim user-text)
                                          :role         :writer}
                              :success   [::invite.success app-id]
                              :error     [::command.error]
                              :timed-out [::command.error]}]]})))

(rf/reg-event-fx ::invite.success
  (fn [cofx [_ _resp app-id]]
    (nutil/merge-every-fn cofx
                          [[reload-fx app-id]
                           [close-dialog-fx]
                           [cleanup-fx]])))

(rf/reg-event-fx ::command.error
  (fn [{:keys [db]} [_ {:keys [text existing-role]}]]
    (let [fixed-email? (-> db ::data :fixed-email?)
          show-remove? (and fixed-email?
                            (some (fn [role] (= role existing-role))
                                  #{"reader" "guest"}))]
      {:db (-> db
               (assoc-in [::data :all-disabled?] false)
               (assoc-in [::data :error] text)
               (assoc-in [::data :show-remove?] show-remove?))
       :fx [(when show-remove?
              [:dialog/resize])]})))

(defn invite-button
  []
  (when (not (<sub [::show-remove?]))
    [ui/icon-button
     {:icon      :circle-plus
      :text-loc  :application.inviteSend
      :wait?     (<sub [::all-disabled?])
      :disabled? (<sub [::invite-disabled?])
      :on-click  (fn [e]
                   (.preventDefault e)
                   (>evt [::invite-clicked]))}]))

(defn view
  []
  (when (<sub [::doc-id])
    [:div.content.authorize-person-content
     [:form
      [description-field]
      [email-field]
      [text-field]
      [error-field]
      [remove-button]
      [invite-button]]]))

(defn show-invite-dialog-fx
  "Show dialog for inviting a new (or an existing) user to a document

  * `app-id` -- Application ID
  * `doc-name` -- Name of the document
  * `doc-id` -- Document ID
  * `path` -- Path to the field
  * `auth-id` -- (optional) User ID of the selected user
  * `email` -- (optional) Email of the selected user "
  [{:keys [db]} [_ {:keys [app-id _auth-id doc-id doc-name email path] :as args}]]
  {:pre [(string? app-id) (string? doc-id) (string? doc-name) (vector? path)]}
  (let [fixed-email? (ui-util/valid-email? email)]
    {:db (cond-> (-> db
                     (assoc ::data args)
                     (assoc-in [::data :fixed-email?] fixed-email?))
           (not fixed-email?) (assoc-in [::data :user-text] (common/loc :invite.default-text)))
     :fx [[:dialog/show {:ltitle           :application.addInvite
                         :size             :autosized
                         :type             :react
                         :dialog-component [view]
                         :minContentHeight "440px"
                         :cancel-callback  (fn [_] (>evt [::dialog-closed]))}]]}))

(rf/reg-event-fx ::show-invite-dialog
  show-invite-dialog-fx)
