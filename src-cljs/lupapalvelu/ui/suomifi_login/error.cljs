(ns lupapalvelu.ui.suomifi-login.error
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]))

(defmethod router/route-init-fn :suomifi-error
  [_]
  (fn [{:keys [db]} [_ visible?]]
    {:db (assoc db ::visible? visible?)}))

(rf/reg-sub ::visible?
  :-> ::visible?)

(defn view []
  (when (<sub [::visible?])
    [:div
     [:h1 (common/loc "login.suomifi-error-title")]
     [:p (common/loc "login.suomifi-error-info")]
     [:a {:href  (str "/login/" js/loc.currentLanguage)
          :class "btn btn-primary"}
      (common/loc "login.back-to-login")]]))
