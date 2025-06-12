(ns lupapalvelu.ui.user
  "Wrapper of sorts for `current-user.js`."
  (:require [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(defn ^js current-user []
  ;; `currentUser` might not always exists, e.g. in public bulletins
  (when (exists? js/lupapisteApp.models.currentUser)
    js/lupapisteApp.models.currentUser))

(defn current-user-id []
  (.id (current-user)))

(defn has-role?
  []
  (boolean
    (when-let [user (current-user)]
      (ss/not-blank? (.role user)))))

(defn financial-authority? []
  (boolean
    (when-let [user (current-user)]
      (.isFinancialAuthority user))))

(defn authority? []
  (boolean
    (when-let [user (current-user)]
      (.isAuthority user))))

(defn applicant? []
  (boolean
    (when-let [user (current-user)]
      (.isApplicant user))))

(defn pure-digitizer? []
  (auth/global-auth? :user-is-pure-digitizer))

(defn virtual?
  "User is either impersonator or oir-authority."
  []
  (.virtual (current-user)))

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
