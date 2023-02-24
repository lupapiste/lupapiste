(ns lupapalvelu.ui.user
  "Wrapper of sorts for `current-user.js`."
  (:require [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]))

(defn current-user []
  js/lupapisteApp.models.currentUser)

(defn financial-authority? []
  (.isFinancialAuthority (current-user)))

(defn authority? []
  (.isAuthority (current-user)))

(defn pure-digitizer? []
  (auth/global-auth? :user-is-pure-digitizer))

(defn default-filter-id
  "`filter-type` is either `:application` (default), `:foreman` or `:company`"
  ([filter-type]
   (get (common/->cljs (.-defaultFilter (current-user)))
        (case filter-type
          :application :id
          :foreman     :foremanFilterId
          :company     :companyFilterId)))
  ([] (default-filter-id :application)))

(defn filters
  "`filter-type` is either `:application` (default), `:foreman` or `:company`"
  ([filter-type]
   (let [user (current-user)]
     (common/->cljs (case filter-type
                      :application (.applicationFilters user)
                      :foreman     (.foremanFilters user)
                      :company     (.companyFilters user)))))
  ([] (filters :application)))

(defn refresh-user-data [db]
  (assoc db ::user-data (common/->cljs (current-user))))

(defn user-data [db]
  (::user-data db))

(rf/reg-event-db
  ::refresh-user-data
  (fn [db _]
    (refresh-user-data db)))

(rf/reg-sub
  ::user-data
  (fn [db _]
    (user-data db)))
