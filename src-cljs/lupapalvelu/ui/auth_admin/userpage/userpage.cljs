(ns lupapalvelu.ui.auth-admin.userpage.userpage
  (:require [clojure.string :as s]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.auth-admin.userpage.state :as state]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [rum.core :as rum]
            [cljs.pprint :as pprint]))

(defn- set-toggle [a-set elem]
  (if (a-set elem)
    (disj a-set elem)
    (conj a-set elem)))

(defn- save-org-info-to-state [org]
  (let [allowed-roles (:allowedRoles org)
        org-id        (keyword (:id org))]
    (reset! state/allowed-roles allowed-roles)
    (reset! state/org-id org-id)))

(defn- get-organization []
  (query "organization-by-user"
         (fn [result]
           (-> result
               :organization
               (save-org-info-to-state)))))

(defn- get-auth-user []
  (let [auth-user-id (.unwrap js/ko @state/auth-user-id-observable)]
    (query "user-for-userpage"
           (fn [result]
             (->> result
                  :data
                  (reset! state/auth-user)))
           :auth-user-id auth-user-id)))

(defn- role-checkbox [current-roles label]
  [:div {:key (str "role-check-box-" label)}
   [:label
    [:input {:type      "checkbox"
             :checked   (@current-roles label)
             :on-change #(swap! current-roles set-toggle label)}]
    [:span (loc (str "authorityrole." label))]]])

(defn- update-roles [roles]
  (let [success-fn      (fn []
                          (reset! state/saving-roles? false)
                          (get-auth-user))
        params          {:command "update-user-roles"
                         :show-saved-indicator? true
                         :success success-fn}
        email           (:email @state/auth-user)
        roles-as-vector (vec @roles)]
    (reset! state/saving-roles? true)
    (command params :email email :roles roles-as-vector)))

(defn- current-auth-user-roles-as-set [user]
  (let [org-id @state/org-id]
    (-> user
        :orgAuthz
        (get org-id)
        (set))))

(rum/defc edit-auth-user-roles < rum/reactive
  []
  (let [current-auth-user (rum/react state/auth-user)
        current-roles-set (atom (current-auth-user-roles-as-set current-auth-user))
        disabled? (not= @current-roles-set
                       (-> current-auth-user :orgAuthz (get @state/org-id) (set)))
        saving? (rum/react state/saving-roles?)]
    [:div
     [:h2 (loc "userpage.roles-title")]
     [:form
      (for [role (rum/react state/allowed-roles)]
        (role-checkbox current-roles-set role))
      [:button.primary {:disabled disabled?
                        :on-click #(update-roles current-roles-set)}
       (if saving?
         [:i.lupicon.wait.spin.lupicon-refresh]
         [:i.lupicon-save])
       [:span (loc "save")]]]]))

(defn- save-change-in-field [user-info attr event]
  (swap! user-info assoc attr (.. event -target -value)))

(defn- info-textfield [user-info attr value]
  [:span {:key (str "info-textfield-" attr)}
   [:label.form-label (loc (str "userinfo." (name attr)))
   [:input.form-input {:type "text"
                       :name attr
                       :value value
                       :on-change (partial save-change-in-field user-info attr)}]]])

(defn- update-user-info [user-info]
  (let [success-fn (fn []
                     (reset! state/saving-info? false)
                     (get-auth-user))
        params     {:command "update-auth-info"
                    :show-saved-indicator? true
                    :success success-fn}
        firstName  (:firstName @user-info)
        lastName   (:lastName @user-info)
        email      (:email @state/auth-user)
        new-email  (:email @user-info)]
    (reset! state/saving-info? true)
    (command params :firstName firstName
                    :lastName lastName
                    :email email
                    :new-email new-email)))

(defn- admin-and-user-have-same-email-domain [auth-user]
  (let [auth-admin-email (get-user-field "email")
        auth-user-email (:email auth-user)
        email-domain-picker (fn [email] (-> email (s/split #"@") (last)))]
    (= (email-domain-picker auth-user-email)
       (email-domain-picker auth-admin-email))))

(defn- auth-admin-can-edit-auth-user-info? [auth-user]
  (let [user-has-one-org? (-> auth-user :orgAuthz (keys) (count) (= 1))
        same-domain? (admin-and-user-have-same-email-domain auth-user)]
    (not (and user-has-one-org? same-domain?))))

(rum/defc edit-auth-user-info < rum/reactive
  []
  (let [user (rum/react state/auth-user)
        saving? (rum/react state/saving-info?)
        user-info (atom {:firstName (:firstName user)
                         :lastName  (:lastName user)
                         :email     (:email user)})
        disabled (auth-admin-can-edit-auth-user-info? user)]
    [:div
     [:h2 (loc "userpage.user-info-title")]
     [:form
      (map #(info-textfield user-info % (get user %)) [:firstName :lastName :email])
      [:button.primary {:disabled disabled
                        :on-click #(update-user-info user-info)}
       (if saving?
         [:i.wait.spin.lupicon-refresh]
         [:i.lupicon-save])
       [:span (loc "save")]]]]))

(defn init [init-state props]
  (let [[                                                   ;auth-model
         auth-user-id-observable] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! state/component-state assoc                      ;:auth-model {:global-auth-model auth-model}
                                       :auth-user-id-observable auth-user-id-observable)
    (get-organization)
    (get-auth-user)
    init-state))

(rum/defc edit-auth-user-view < rum/reactive
  {:init         init
   :will-unmount (fn [& _] (reset! state/component-state {}))}
  [auth-user-id-observable                                  ;auth-model
   ]
  (let [user (rum/react state/auth-user)]
    [:div
     [:h1 (str (loc "userpage.title") ", " (:firstName user) " " (:lastName user))]
     [:div (loc "userpage.desc")]
     (edit-auth-user-roles)
     [:div.hr]
     (edit-auth-user-info)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (edit-auth-user-view (:auth-user-id-observable @args)
                                  ;   (:auth-model @args)
                                  )
             (.getElementById js/document (:dom-id @args))))

(defn ^export start [domId componentParams]
  (swap! args assoc                                         ;:auth-model (aget componentParams "authModel")
                    :auth-user-id-observable (aget componentParams "authUserIdObservable")
                    :dom-id (name domId))
  (mount-component))
