(ns lupapalvelu.ui.suomifi-login.registration
  "Registration page shown when authenticated user has no Lupapiste account"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.suomifi-login.components :refer [logout-button]]
            [re-frame.core :as rf]))

(defmethod router/route-init-fn :suomifi-registration
  [_]
  (fn [{:keys [db]} [_ visible?]]
    {:db (assoc db ::visible? visible?)}))

(rf/reg-sub ::visible?
  :-> ::visible?)

(defn view []
  (when (<sub [::visible?])
    [:div
     [:h1 (common/loc :login.account-not-found)]
     [:p (common/loc :login.account-not-found-instructions)]
     [:a {:href  (str "/app/" js/loc.currentLanguage "/welcome#!/register2")
          :class "btn btn-primary"}
      (common/loc :welcome.register)]
     [:div.gap--t4
      [logout-button {:info-loc     :login.account-suomifi
                      :button-class :secondary}]]]))
