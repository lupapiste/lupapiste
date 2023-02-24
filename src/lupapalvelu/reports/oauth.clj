(ns lupapalvelu.reports.oauth
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.excel :as excel]
            [monger.operators :refer :all])
  (:import [java.util Date]))

(defn- login-rows [client-id start-ts end-ts]
  (when-let [tokens (not-empty (mongo/select :token
                                             {:token-type     :oauth-code
                                              :data.client-id client-id
                                              :created        {$gte start-ts
                                                               $lte end-ts}}
                                             [:created :user-id]))]
    (let [user-ids  (some->> tokens (map :user-id) set not-empty)
          usernames (->> (mongo/select :users {:_id {$in user-ids}} [:username])
                         (map (juxt :id :username))
                         (into {}))]
      (for [{:keys [created user-id]} tokens]
        {:username (get usernames user-id)
         :date     (Date. created)}))))

(defn logins-report
  ([client-id start-ts end-ts lang]
   (let [loc (partial i18n/localize lang)
         wb  (excel/create-workbook
               [{:sheet-name (loc :oauth.report.sheet)
                 :header     [(loc :digitizer.report.excel.header.date)
                              (loc :user)]
                 :row-fn     (juxt :date :username)
                 :data       (login-rows client-id start-ts end-ts)}])]
     (excel/xlsx-stream wb)))
  ([client-id start-ts end-ts]
   (logins-report client-id start-ts end-ts :fi)))
