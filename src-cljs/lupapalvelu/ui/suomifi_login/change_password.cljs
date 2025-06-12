(ns lupapalvelu.ui.suomifi-login.change-password
  "Authenticated user wishes to change the password of one of their Lupapiste accounts"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.password-reset :as pw]
            [lupapalvelu.ui.misc :as misc]
            [lupapalvelu.ui.suomifi-login.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.util :as util]))

(defmethod router/route-init-fn :suomifi-change-password
  [_]
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      (let [user-id (-> db router/db->path-parameters :user-id)]
        {:db (assoc db ::data {:visible?   true
                               :id-counter 0})
         :fx [(when-not (subs/users? db)
                [:dispatch [::subs/fetch-users
                            {:on-success (fn [{:keys [accounts]}]
                                           (when-not (util/find-by-id user-id accounts)
                                             (rf/dispatch [:route/push :suomifi-error])))}]])]})
      {:db (dissoc db ::data)})))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> (comp boolean :visible?))

(rf/reg-sub ::id-counter
  :<- [::data]
  :-> :id-counter)

(rf/reg-sub ::pending?
  :<- [::data]
  :-> (comp boolean :pending?))

(rf/reg-sub ::finished?
  :<- [::data]
  :-> (comp boolean :finished?))

(rf/reg-sub ::user-id
  :<- [:route/path-parameters]
  :-> :user-id)

(rf/reg-sub ::user
  :<- [::user-id]
  :<- [::subs/users]
  (fn [[user-id users]]
    (util/find-by-id user-id users)))

(rf/reg-sub ::email
  :<- [::user]
  :-> :email)

(rf/reg-event-fx ::click-back
  (fn [_ _]
    {::misc/navigate-back true}))

(rf/reg-event-fx ::submit.success
  (fn [{:keys [db]} _]
    {:db (-> db
             (update-in [::data :id-counter] inc)
             (assoc-in [::data :finished?] true)
             (assoc-in [::data :pending?] false))}))

(rf/reg-event-fx ::submit.failure
  (fn [{:keys [db]} [_ {:keys [text]}]]
    {:db          (assoc-in db [::data :pending?] false)
     :dialog/show {:ltitle :error.dialog.title
                   :ltext  text}}))

(rf/reg-event-fx ::submit
  (fn [{:keys [db]} [_ {:keys [user-id password]}]]
    {:db             (assoc-in db [::data :pending?] true)
     :action/command {:name                  :change-password-suomifi
                      :params                {:userId   user-id
                                              :password password}
                      :show-saved-indicator? true
                      :success               ::submit.success
                      :error                 ::submit.failure}}))

(defn- submit-button
  [*password]
  (let [password (deref *password)
        user-id  (<sub [::user-id])]
    [:button.primary
     {:disabled (or (nil? password)
                    (<sub [::pending?]))
      :on-click #(>evt [::submit {:password password
                                  :user-id  user-id}])}
     (common/loc :setpw.send)]))

(defn- back-button
  []
  [:button.secondary
   {:on-click #(>evt [::click-back])}
   (common/loc :back)])

(defn- traficom-link
  []
  (case js/loc.currentLanguage
    "en" "https://www.kyberturvallisuuskeskus.fi/en/ncsc-news/instructions-and-guides/longer-better-how-create-strong-password"
    "sv" "https://www.kyberturvallisuuskeskus.fi/sv/aktuellt/anvisningar-och-guider/ju-langre-desto-battre-sa-har-skapar-du-ett-bra-losenord"
    "https://www.kyberturvallisuuskeskus.fi/fi/ajankohtaista/ohjeet-ja-oppaat/pidempi-parempi-nain-teet-hyvan-salasanan"))

(defn- success
  []
  [:div.flex-col
   [:div.flex.flex--gap1
    [:i.lupicon-check.positive {:aria-hidden true}]
    (common/loc :setpw.change-password-proceed)]
   [back-button :back]])

(defn view
  []
  (when (<sub [::visible?])
    (r/with-let [*password (r/atom nil)]
      [:<>
       [:h1 (common/loc :setpw.title)]
       [:h2 (common/loc :setpw.help-title)]
       [:p (common/loc :setpw.help)
        [:a {:href   (traficom-link)
             :target "_blank"}
         (common/loc :setpw.link)]
        " "
        (common/loc :setpw.help-link)]
       [:p (common/loc :setpw.current) " " [:strong (<sub [::email])]]
       [:p (common/loc :setpw.info)]
       ;; Assign new key every time password is changed successfully
       ;; -> clears any state within `pw/view`.
       ^{:key (<sub [::id-counter])}
       [pw/view {:on-change       (fn [password] (reset! *password password))
                 :quality-options {:user-id (<sub [::user-id])}
                 :disabled?       (<sub [::finished?])}]
       [:div.flex.flex--gap2
        (if (<sub [::finished?])
          [success]
          [:<>
           [submit-button *password]
           [back-button]])]])))
