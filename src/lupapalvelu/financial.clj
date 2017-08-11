(ns lupapalvelu.financial
  (:require
    [monger.operators :refer :all]
    [sade.core :refer :all]
    [lupapalvelu.user :as usr]))

(defn fetch-organization-financial-handlers [org-id]
  (let [query {$and [{:role "financialAuthority"}, (usr/org-authz-match [org-id])]}
        financial-handlers (usr/find-users query)]
    (ok :data financial-handlers)))

(defn delete-organization-financial-handler [email org-id]
  (usr/update-user-by-email email {:role "financialAuthority"} {$unset {(str "orgAuthz." org-id) ""}}))
