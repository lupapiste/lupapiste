(ns lupapalvelu.ui.auth-admin.edit-authority.edit-info
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.edit-authority.subs :as edit-authority-subs]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as edit-authority-roles]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]))

(defn- info-textfield [attr value disabled? on-change]
  [:div {:key (str "info-textfield-" attr)}
   [:label.edit-user-info-label (loc (str "userinfo." (name attr)))
    [:input.form-input {:type      "text"
                        :disabled  disabled?
                        :name      attr
                        :value     value
                        :on-change on-change}]]])

(defn edit-authority-info []
  (let [authority-id            (<sub [::edit-authority-subs/authority-id])
        multiple-org-authority? (<sub [::edit-authority-subs/multiple-org-authority?])
        not-same-domain?        (<sub [::edit-authority-subs/different-domain-with-admin?])
        ad-login?               (<sub [::edit-authority-subs/ad-login?])
        disabled?               (or multiple-org-authority? not-same-domain? ad-login?)
        modified-info           (<sub [::edit-authority-subs/edited-authority])]
    [:div.edit-authority-info
     [:h2 (loc "auth-admin.edit-authority.user-info-title")]
     (when ad-login?
       [edit-authority-roles/info-box :auth-admin.edit-authority.cannot-edit-ad-user])
     (doall
       (for [field [:firstName :lastName :email]]
         (info-textfield field
                         (get modified-info field "")
                         disabled?
                         #(>evt [:value/set [::edit-authority-subs/authorities authority-id :edited-data field] (.. % -target -value)]))))

     (when-not ad-login?
       [:<>
        (components/icon-button {:icon      :lupicon-save
                                 :wait?     (<sub [::edit-authority-subs/loading?])
                                 :on-click  #(>evt [::edit-authority-subs/update-info])
                                 :disabled? disabled?
                                 :text-loc  "save"
                                 :class     "primary"})
        (when disabled?
          [:p.error-message.edit-info
           (cond
             multiple-org-authority? (loc :error.authority-has-multiple-orgs)
             not-same-domain? (loc :error.auth-admin-and-authority-have-different-domains))])])]))
