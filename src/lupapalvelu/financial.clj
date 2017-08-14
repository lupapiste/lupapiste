(ns lupapalvelu.financial
  (:require
    [monger.operators :refer :all]
    [sade.core :refer :all]
    [lupapalvelu.user :as usr]
    [lupapalvelu.user-utils :as uu]))

(defn fetch-organization-financial-handlers [org-id]
  (let [query {$and [{:role "financialAuthority"}, (usr/org-authz-match [org-id])]}
        financial-handlers (usr/find-users query)]
    (ok :data financial-handlers)))

(defn create-financial-handler [user-data org-id user]
  (let [user-data (assoc user-data :organization org-id)
        user-data (assoc user-data :role "financialAuthority")
        new-user (:user (uu/create-and-notify-user user user-data))]
    (usr/update-user-by-email (:email new-user) {:role "financialAuthority"} {$set {(str "orgAuthz." org-id) ["reader" "commenter"]}})))

(defn delete-organization-financial-handler [email org-id]
  (usr/update-user-by-email email {:role "financialAuthority"} {$unset {(str "orgAuthz." org-id) ""}}))
